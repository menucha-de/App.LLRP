package havis.llrpservice.server.service.fsm.rfc;

import havis.llrpservice.server.service.fsm.LLRPServiceInstanceRuntimeData;
import havis.llrpservice.server.service.fsm.lllrp.LLRPRuntimeData;

public class FSMRFCExecuteResponseEvent extends FSMRFCEvent {

	private LLRPServiceInstanceRuntimeData llrpServiceInstanceRuntimeData;

	public void setRuntimeData(LLRPServiceInstanceRuntimeData llrpServiceInstanceRuntimeData,
			LLRPRuntimeData llrpRuntimeData, RFCRuntimeData runtimeData) {
		this.llrpServiceInstanceRuntimeData = llrpServiceInstanceRuntimeData;
		setRuntimeData(llrpRuntimeData, runtimeData);
	}

	public LLRPServiceInstanceRuntimeData getLLRPServiceInstanceRuntimeData() {
		return llrpServiceInstanceRuntimeData;
	}

	@Override
	public String toString() {
		return "FSMRFCExecuteResponseEvent [" + super.toString() + "]";
	}
}