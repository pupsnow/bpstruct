package ee.ut.bpstruct.bpmn.test.cyclic;

import ee.ut.bpstruct.bpmn.test.StructuringTest;

public class Model10790Test extends StructuringTest {

	public Model10790Test() {
		this.MODEL_NAME = "model10000";
		this.MODEL_PATH_TPL = "models/cyclic/%s.bpmn";
		this.OUTPUT_PATH_TPL = "tmp/cyclic/%s.dot";
		this.CAN_STRUCTURE = true;
	}

}

