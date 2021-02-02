package havis.llrpservice.server.service.fsm.gpio;

import havis.llrpservice.server.service.fsm.LLRPServiceInstanceRuntimeData;
import havis.llrpservice.server.service.fsm.gpio.GPIORuntimeData;
import havis.llrpservice.server.service.fsm.lllrp.LLRPRuntimeData;
import havis.llrpservice.server.service.fsm.rfc.RFCRuntimeData;

public class FSMGPIOGetConfigurationResponseEvent extends FSMGPIOEvent {

	private LLRPServiceInstanceRuntimeData llrpServiceInstanceRuntimeData;
	private RFCRuntimeData rfcRuntimeData;

	public void setRuntimeData(LLRPServiceInstanceRuntimeData llrpServiceInstanceRuntimeData,
			LLRPRuntimeData llrpRuntimeData, RFCRuntimeData rfcRuntimeData,
			GPIORuntimeData runtimeData) {
		this.llrpServiceInstanceRuntimeData = llrpServiceInstanceRuntimeData;
		setRuntimeData(llrpRuntimeData, runtimeData);
		this.rfcRuntimeData = rfcRuntimeData;
	}

	public LLRPServiceInstanceRuntimeData getLLRPServiceInstanceRuntimeData() {
		return llrpServiceInstanceRuntimeData;
	}

	public RFCRuntimeData getRFCRuntimeData() {
		return rfcRuntimeData;
	}

	@Override
	public String toString() {
		return "FSMGPIOGetConfigurationResponseEvent [llrpServiceInstanceRuntimeData="
				+ llrpServiceInstanceRuntimeData + ", rfcRuntimeData=" + rfcRuntimeData + ", "
				+ super.toString() + "]";
	}
}