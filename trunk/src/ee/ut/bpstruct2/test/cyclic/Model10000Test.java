package ee.ut.bpstruct2.test.cyclic;

import ee.ut.bpstruct2.test.StructuringTest;

public class Model10000Test extends StructuringTest {

	public Model10000Test() {
		this.MODEL_NAME = "model10000";
		this.MODEL_PATH_TPL = "models/cyclic/%s.json";
		this.OUTPUT_PATH_TPL = "tmp/cyclic/%s.dot";
		this.CAN_STRUCTURE = true;
	}

}

