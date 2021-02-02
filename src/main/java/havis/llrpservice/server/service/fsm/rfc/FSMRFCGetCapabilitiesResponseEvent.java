package havis.llrpservice.server.service.fsm.rfc;

import havis.llrpservice.server.service.fsm.LLRPServiceInstanceRuntimeData;
import havis.llrpservice.server.service.fsm.gpio.GPIORuntimeData;
import havis.llrpservice.server.service.fsm.lllrp.LLRPRuntimeData;

public class FSMRFCGetCapabilitiesResponseEvent extends FSMRFCEvent {

	private GPIORuntimeData gpioRuntimeData;
	private LLRPServiceInstanceRuntimeData llrpServiceInstanceRuntimeData;

	public void setRuntimeData(LLRPServiceInstanceRuntimeData llrpServiceInstanceRuntimeData,
			LLRPRuntimeData llrpRuntimeData, RFCRuntimeData runtimeData,
			GPIORuntimeData gpioRuntimeData) {
		this.llrpServiceInstanceRuntimeData = llrpServiceInstanceRuntimeData;
		setRuntimeData(llrpRuntimeData, runtimeData);
		this.gpioRuntimeData = gpioRuntimeData;
	}

	public LLRPServiceInstanceRuntimeData getLLRPServiceInstanceRuntimeData() {
		return llrpServiceInstanceRuntimeData;
	}

	public GPIORuntimeData getGPIORuntimeData() {
		return gpioRuntimeData;
	}

	@Override
	public String toString() {
		return "FSMRFCGetCapabilitiesResponseEvent [gpioRuntimeData=" + gpioRuntimeData
				+ ", llrpServiceInstanceRuntimeData=" + llrpServiceInstanceRuntimeData + ", "
				+ super.toString() + "]";
	}
}