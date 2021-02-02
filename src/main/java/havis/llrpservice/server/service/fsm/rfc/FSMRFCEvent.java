package havis.llrpservice.server.service.fsm.rfc;

import havis.llrpservice.server.service.fsm.FSMEvent;
import havis.llrpservice.server.service.fsm.lllrp.LLRPRuntimeData;

public class FSMRFCEvent implements FSMEvent {

	private LLRPRuntimeData llrpRuntimeData;
	private RFCRuntimeData runtimeData;

	public void setRuntimeData(LLRPRuntimeData llrpRuntimeData,
			RFCRuntimeData runtimeData) {
		this.llrpRuntimeData = llrpRuntimeData;
		this.runtimeData = runtimeData;
	}

	public RFCRuntimeData getRuntimeData() {
		return runtimeData;
	}

	public LLRPRuntimeData getLLRPRuntimeData() {
		return llrpRuntimeData;
	}

	@Override
	public String toString() {
		return "FSMRFCEvent [runtimeData=" + runtimeData + ", llrpRuntimeData="
				+ llrpRuntimeData + "]";
	}
}