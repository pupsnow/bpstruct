/* 
 * Copyright (C) 2010 - Luciano Garcia Banuelos, Artem Polyvyanyy
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ee.ut.bpstruct;

import hub.top.uma.DNode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ee.ut.bpstruct.unfolding.Unfolding;
import ee.ut.graph.moddec.ColoredGraph;

public class BehavioralProfiler {
	
	enum OrderingRelation {NONE, PRECEDENCE, CONFLICT, CONCURRENCY};
	/**
	 * Matrix containing the ordering relations of the events
	 */
	OrderingRelation[][] eventRels = null;
	ColoredGraph orgraph = null;
	Unfolding unf = null;
	Map<DNode, Integer> entryMap = new LinkedHashMap<DNode, Integer>();
	List<DNode> entries = new LinkedList<DNode>();
	Map<Integer, String> map = new HashMap<Integer, String>();
	
	public BehavioralProfiler(Unfolding unf, Map<String, Integer> tasks, Map<String, Integer> clones) {
		this.unf = unf;
		computePrefixRelations();
		completePrefixRelations();
		
		Set<DNode> taus = new HashSet<DNode>();
		checkConflict(tasks, taus);

		updateLabels(tasks, taus, clones, map);
		computeOrderingRelationsGraph(map);
	}
	
	/*
	 * This constructor is temporally inserted for gathering the information about the ordering relations
	 * in different stages of their computation (i.e. before and after updating the causality relation).
	 */
	public BehavioralProfiler(Helper helper, Unfolding unf, Map<String, Integer> tasks, Map<String, Integer> clones) {
		this.unf = unf;
		PrintStream out = null;
		try {
			File dir = helper.getDebugDir();
			String name = String.format("unfolding_%s.dot", helper.getModelName());
			out = new PrintStream(new File(dir, name));
			out.println(unf.toDot());
			out.close();
			
			name = String.format("ordrels_%s.txt", helper.getModelName());
			out = new PrintStream(new File(dir, name));
		} catch (Exception e) {
			e.printStackTrace();
			out = System.out;
		}
		
		computePrefixRelations();
		Map<Integer, String> mapp = new HashMap<Integer, String>();
		updateLabels(tasks, new HashSet<DNode>(), new HashMap<String, Integer>(), mapp);
		out.println("------------ Before updating causality");
		out.println(serializeOrderRelationMatrix(mapp));
		
		completePrefixRelations();
		mapp = new HashMap<Integer, String>();
		updateLabels(tasks, new HashSet<DNode>(), new HashMap<String, Integer>(), mapp);
		out.println("------------ After updating causality");
		out.println(serializeOrderRelationMatrix(mapp));
		
		Set<DNode> taus = new HashSet<DNode>();
		checkConflict(tasks, taus);
		
		updateLabels(tasks, taus, clones, map);
		
		out.println("------------ Label map ");
		for (Integer key: map.keySet())
			out.printf("%d\t %s\n", key, map.get(key));

		computeOrderingRelationsGraph(map);
	}

	public String serializeOrderRelationMatrix() {
		return serializeOrderRelationMatrix(map);
	}
	public String serializeOrderRelationMatrix(Map<Integer, String> map) {
		ByteArrayOutputStream barray = new ByteArrayOutputStream();
		PrintStream out = new PrintStream(barray);
		
		for (int i: map.keySet()) {
			out.printf("%3d ", i);
			for (int j: map.keySet()) {
				if (eventRels[i][j] == OrderingRelation.CONCURRENCY)
					out.print(" @");
				else if (eventRels[i][j] == OrderingRelation.CONFLICT)
					out.print(" #");
				else if (eventRels[i][j] == OrderingRelation.PRECEDENCE)
					out.print(" <");
				else
					out.print(" _");
			}
			out.println();
		}
		return barray.toString();
	}
	
	// At this moment, the code verifies if a task "t" requires a task "tau" to
	// preserve the conflict relation. In the future, we have to consider to
	// use the logical predicates associated to outgoing edges of XOR splits
	// as the label of the corresponding transitions.
	private void checkConflict(Map<String, Integer> tasks, Set<DNode> taus) {		
		for (int i = 0; i < entries.size(); i++) {
			DNode ev = entries.get(i);
			int index = -1;
			String label = unf.getProperName(ev);
			boolean corresp = false;
			if (tasks.containsKey(label)) {
				for (int j = 0; j < entries.size(); j++) {
					if (eventRels[i][j] == OrderingRelation.CONFLICT) {
						DNode evp = entries.get(j);
						if (index < 0)
							index = j;
						if (tasks.containsKey(unf.getProperName(evp))) {
							corresp = true;
							break;
						}
					}
				}
				if (index >= 0 && !corresp) {
					taus.add(entries.get(index));
				}
			}
		}
	}
	
	private void updateLabels(Map<String, Integer> tasks,
			Set<DNode> taus, Map<String, Integer> clones, Map<Integer, String> map) {
		int tauCount = 0;
		HashMap<String, Integer> labelCount = new HashMap<String, Integer>();
		
		for (DNode ev: unf.getAllEvents()) {
			String label = unf.getProperName(ev);
			if (tasks.containsKey(label)) {
				int count = 0;
				if (labelCount.containsKey(label)) {
					count = labelCount.get(label);
					labelCount.put(label, ++count);
					Integer vertex = tasks.get(label);
					label += Integer.toString(count);
					clones.put(label, vertex);
				} else
					labelCount.put(label, 0);
				map.put(entryMap.get(ev), label);
			}
			if (taus.contains(ev)) {
				String labelp = "tau" + tauCount++;
				map.put(entryMap.get(ev), labelp);
				clones.put(labelp, -1);
			}
		}
	}
	
	private void computeOrderingRelationsGraph(Map<Integer, String> map) {
		orgraph = new ColoredGraph();
		
		// add vertices
		for (String label: map.values())
			orgraph.addVertex(label);
		
		// add edges
		for (int i = 0; i < eventRels.length; i++) {
			if (!map.containsKey(i)) continue;
			for (int j = i + 1; j < eventRels.length; j++) {
				if (!map.containsKey(j)) continue;
				if (eventRels[i][j] == OrderingRelation.CONCURRENCY)
					;
				else if (eventRels[i][j] == OrderingRelation.CONFLICT) {
					orgraph.addEdge(map.get(i), map.get(j));
					orgraph.addEdge(map.get(j), map.get(i));
				}
				else if (eventRels[i][j] == OrderingRelation.PRECEDENCE)
					orgraph.addEdge(map.get(i), map.get(j));
				else if (eventRels[j][i] == OrderingRelation.PRECEDENCE)
					orgraph.addEdge(map.get(j), map.get(i));
			}
		}
	}

	public ColoredGraph getOrderingRelationsGraph() {
		return orgraph;
	}

	
	// ------------------------------------------------------------------------
	// It might be preferable to use a topological order approach to simplify all loops
	// used for updating/propagating ordering relations.
	
	/**
	 * Computes ordering relations of all events in a Complete Prefix brprocolding
	 * (This method implements the first phase in Algorithm 1).
	 * 
	 * @param brproc	The complete prefix brprocolding
	 */
	private void computePrefixRelations() {
		// STEP 1: Initialize all ordering relations to CONCURRENCY
		eventRels = new OrderingRelation[unf.getAllEvents().size()][unf.getAllEvents().size()];
		for (int i = 0; i < eventRels.length; i++)
			for (int j = 0; j < eventRels.length; j++)
				eventRels[i][j] = OrderingRelation.CONCURRENCY;
		
		int index = 0;
		
		// STEP 2
		//   - Outer-most loop: Traverse the brprocolding using a pre-order DFS strategy.
		//   - Nested loops are implemented in updateEventRelations method.
		HashSet<DNode> visited = new HashSet<DNode>();
		LinkedList<DNode> worklist = new LinkedList<DNode>();
		
		DNode entry = unf.getInitialConditions().get(0);
		visited.add(entry);
		worklist.addAll(Arrays.asList(entry.post));
		while (!worklist.isEmpty()) {
			DNode ev = worklist.removeFirst();
			if (!entryMap.containsKey(ev)) {
				entryMap.put(ev, index++);
				entries.add(ev);
			}
			if (visited.containsAll(Arrays.asList(ev.pre))) {
				updateEventRelations(ev);					// Critical stuff !!
				for (DNode succ: ev.post) {
					visited.add(succ);
					if (succ.post != null) {
						for (DNode e2: succ.post)
							if (!worklist.contains(e2))
								worklist.add(e2);
					}
				}
			} else
				worklist.addLast(ev);
		}
	}
	
	/**
	 * It updates the ordering relations for given event (nested loops of phase 2 in Algorithm 1).
	 * (This method is called from computePrefixRelations).
	 * 
	 * @param ev_i   The event for which ordering relations are computed/updated
	 */
	private void updateEventRelations(DNode ev_i) {
		for (DNode cond: ev_i.pre) {
			if (cond.pre.length != 0) {
				DNode ev_j = cond.pre[0];
				eventRels[entryMap.get(ev_j)][entryMap.get(ev_i)] = OrderingRelation.PRECEDENCE;
				eventRels[entryMap.get(ev_i)][entryMap.get(ev_j)] = OrderingRelation.NONE;		
				for (int k = 0; k < eventRels.length; k++) {
					if (eventRels[entryMap.get(ev_j)][k] == OrderingRelation.NONE) {
						eventRels[k][entryMap.get(ev_i)] = OrderingRelation.PRECEDENCE;
						eventRels[entryMap.get(ev_i)][k] = OrderingRelation.NONE;
					}
					if (eventRels[entryMap.get(ev_j)][k] == OrderingRelation.CONFLICT) {
						eventRels[k][entryMap.get(ev_i)] = OrderingRelation.CONFLICT;
						eventRels[entryMap.get(ev_i)][k] = OrderingRelation.CONFLICT;					
					}
				}
			}
			for (DNode ev_j: cond.post) {
				if (ev_j != ev_i && entryMap.get(ev_j) != null && entryMap.get(ev_i) != null) {
					eventRels[entryMap.get(ev_j)][entryMap.get(ev_i)] = OrderingRelation.CONFLICT;
					eventRels[entryMap.get(ev_i)][entryMap.get(ev_j)] = OrderingRelation.CONFLICT;
					for (int k = 0; k < eventRels.length; k++) {
						if (entryMap.get(ev_i) != k && eventRels[entryMap.get(ev_j)][k] == OrderingRelation.PRECEDENCE) {
							eventRels[k][entryMap.get(ev_i)] = OrderingRelation.CONFLICT;
							eventRels[entryMap.get(ev_i)][k] = OrderingRelation.CONFLICT;		
						}
					}
				}
			}
		}
	}
	
	/**
	 * Updates ordering relations of all events in the local configuration of 
	 * a cutoff event.
	 * (This method implements the second phase in Algorithm 1).
	 * @param x 
	 * 
	 * @param brproc	The complete prefix
	 */
	private void completePrefixRelations() {
		for (DNode cutoff: unf.getCutoffs()) {
			DNode corresponding = unf.getCorr(cutoff);
			
			// Set of common successors to both cutoff and its "corresponding event"
			List<DNode> csuccs = new LinkedList<DNode>();
			for (DNode csucc: corresponding.post)
				for (DNode succ: cutoff.post)
					if (unf.getProperName(csucc).equals(unf.getProperName(succ)) && csucc.post != null && csucc.post.length > 0)
						csuccs.add(csucc.post[0]);
			
			// Update ordering relations for all events in the corresponding local configuration
			for (DNode succ: csuccs)
				for (DNode t: unf.getPrimeConfiguration(cutoff)) {
					eventRels[entryMap.get(t)][entryMap.get(succ)] = OrderingRelation.PRECEDENCE;
					eventRels[entryMap.get(succ)][entryMap.get(t)] = OrderingRelation.NONE;
					for (int i = 0; i < eventRels.length; i++)
						if (eventRels[entryMap.get(succ)][i] == OrderingRelation.PRECEDENCE) {
							eventRels[entryMap.get(t)][i] = OrderingRelation.PRECEDENCE;
							eventRels[i][entryMap.get(t)] = OrderingRelation.NONE;
						}
				}			
		}
	}
}