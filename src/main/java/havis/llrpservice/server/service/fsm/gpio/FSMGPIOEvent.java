package havis.llrpservice.server.service.fsm.gpio;

import havis.llrpservice.server.service.fsm.FSMEvent;
import havis.llrpservice.server.service.fsm.lllrp.LLRPRuntimeData;

public class FSMGPIOEvent implements FSMEvent {

	private GPIORuntimeData runtimeData;
	private LLRPRuntimeData llrpRuntimeData;

	public void setRuntimeData(LLRPRuntimeData llrpRuntimeData,
			GPIORuntimeData runtimeData) {
		this.llrpRuntimeData = llrpRuntimeData;
		this.runtimeData = runtimeData;
	}

	public GPIORuntimeData getRuntimeData() {
		return runtimeData;
	}

	public LLRPRuntimeData getLLRPRuntimeData() {
		return llrpRuntimeData;
	}

	@Override
	public String toString() {
		return "FSMGPIOEvent [runtimeData=" + runtimeData
				+ ", llrpRuntimeData=" + llrpRuntimeData + "]";
	}
}