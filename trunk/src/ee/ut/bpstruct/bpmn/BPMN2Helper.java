/* 
 * Copyright (C) 2010 - Luciano Garcia Banuelos
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
package ee.ut.bpstruct.bpmn;

import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;

import de.bpt.hpi.graph.Graph;

/**
 * This helper class parses BPMN 2.0 files and creates jdbt graph representation of the process graph.
 * Note that the implementation uses JDOM, for simplicity and generality.
 * 
 * @author Luciano Garcia Banuelos
 */
public class BPMN2Helper extends BPMNHelper {
	// log4j ---
	static Logger logger = Logger.getLogger(BPMN2Helper.class);
	
	// OMG BPMN 2.0 URI --- JDOM Namespace
	static Namespace BPMN2NS = Namespace.getNamespace("http://schema.omg.org/spec/BPMN/2.0");
	
	private Element process;
	
	private String modelName;
	
	public BPMN2Helper(String model_path_tpl, String model_name) throws Exception {
		super();
		this.modelName = model_name;
		String fileName = String.format(model_path_tpl, model_name);
		if (logger.isInfoEnabled()) logger.info("Reading BPMN 2.0 file: " + fileName);
		Document doc = new SAXBuilder().build(fileName);
		
		process = doc.getRootElement().getChild("process", BPMN2NS);
		initGraph();
	}
	
	void initGraph() {
		if (logger.isInfoEnabled()) logger.info("Creating graph");
		
		graph = new Graph();
		List<Element> edges = new LinkedList<Element>();
		for (Object obj : process.getChildren())
			if (obj instanceof Element) {
				Element elem = (Element) obj;
				String id = elem.getAttributeValue("id");
				String name = elem.getAttributeValue("name");
				if (elem.getName().equals("task")) {
					Integer vertex = graph.addVertex(name);
					map.put(id, vertex);
					rmap.put(vertex, id);
				} else if (elem.getName().equals("exclusiveGateway")) {
						Integer vertex = graph.addVertex(name);
						map.put(id, vertex);
						rmap.put(vertex, id);
						gwmap.put(vertex, GWType.XOR);
				} else if (elem.getName().equals("parallelGateway")) {
					Integer vertex = graph.addVertex(name);
					map.put(id, vertex);
					rmap.put(vertex, id);
					gwmap.put(vertex, GWType.AND);
				} else if (elem.getName().equals("inclusiveGateway")) {
					Integer vertex = graph.addVertex(name);
					map.put(id, vertex);
					rmap.put(vertex, id);
					gwmap.put(vertex, GWType.OR);
				} else if (elem.getName().equals("sequenceFlow"))
					edges.add(elem);
				else if (logger.isInfoEnabled()) logger.warn("Unprocessed Element: " + elem.getName() + ", id: " + elem.getAttributeValue("id"));
			}

		for (Element flow : edges) {
			Integer src = map.get(flow.getAttributeValue("sourceRef"));
			Integer tgt = map.get(flow.getAttributeValue("targetRef"));
			if (src != null && tgt != null)
				graph.addEdge(src, tgt);
			else {
				logger.fatal("Malformed graph! Dangling edge: " + flow.getAttributeValue("id"));
				throw new RuntimeException("Malformed graph");
			}
		}
		
		if (logger.isInfoEnabled()) logger.info("Graph created");
	}

	public String getModelName() {
		return this.modelName;
	}
}
