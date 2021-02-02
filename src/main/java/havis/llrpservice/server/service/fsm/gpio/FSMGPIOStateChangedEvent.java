package havis.llrpservice.server.service.fsm.gpio;

import havis.llrpservice.server.service.fsm.LLRPServiceInstanceRuntimeData;
import havis.llrpservice.server.service.fsm.lllrp.LLRPRuntimeData;

public class FSMGPIOStateChangedEvent extends FSMGPIOEvent {

	private LLRPServiceInstanceRuntimeData llrpServiceInstanceRuntimeData;

	public void setRuntimeData(LLRPServiceInstanceRuntimeData llrpServiceInstanceRuntimeData,
			LLRPRuntimeData llrpRuntimeData, GPIORuntimeData runtimeData) {
		this.llrpServiceInstanceRuntimeData = llrpServiceInstanceRuntimeData;
		setRuntimeData(llrpRuntimeData, runtimeData);
	}

	public LLRPServiceInstanceRuntimeData getLLRPServiceInstanceRuntimeData() {
		return llrpServiceInstanceRuntimeData;
	}

	@Override
	public String toString() {
		return "FSMGPIOStateChangedEvent [llrpServiceInstanceRuntimeData="
				+ llrpServiceInstanceRuntimeData + ", " + super.toString() + "]";
	}
}