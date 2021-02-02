package havis.llrpservice.server.service.fsm.gpio;

import havis.llrpservice.server.service.fsm.gpio.GPIORuntimeData;
import havis.llrpservice.server.service.fsm.lllrp.LLRPRuntimeData;
import havis.llrpservice.server.service.fsm.rfc.RFCRuntimeData;

public class FSMGPIOSetConfigurationResponseEvent extends FSMGPIOEvent {

	private RFCRuntimeData rfcRuntimeData;

	public void setRuntimeData(LLRPRuntimeData llrpRuntimeData,
			RFCRuntimeData rfcRuntimeData, GPIORuntimeData runtimeData) {
		setRuntimeData(llrpRuntimeData, runtimeData);
		this.rfcRuntimeData = rfcRuntimeData;
	}

	public RFCRuntimeData getRFCRuntimeData() {
		return rfcRuntimeData;
	}

	@Override
	public String toString() {
		return "FSMGPIOSetConfigurationResponseEvent [rfcRuntimeData="
				+ rfcRuntimeData + ", " + super.toString() + "]";
	}
}