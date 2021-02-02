package havis.llrpservice.server.service.fsm.lllrp;

import havis.llrpservice.server.service.fsm.FSMEvent;

public class FSMLLRPEvent implements FSMEvent {

	private LLRPRuntimeData runtimeData;

	public void setRuntimeData(LLRPRuntimeData runtimeData) {
		this.runtimeData = runtimeData;
	}

	public LLRPRuntimeData getRuntimeData() {
		return runtimeData;
	}

	@Override
	public String toString() {
		return "FSMLLRPEvent [runtimeData=" + runtimeData + "]";
	}
}