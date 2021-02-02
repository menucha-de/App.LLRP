package havis.llrpservice.server.service.fsm.lllrp;

import havis.llrpservice.server.service.fsm.LLRPServiceInstanceRuntimeData;
import havis.llrpservice.server.service.fsm.gpio.GPIORuntimeData;
import havis.llrpservice.server.service.fsm.rfc.RFCRuntimeData;

public class FSMLLRPSetReaderConfigEvent extends FSMLLRPEvent {

	private LLRPServiceInstanceRuntimeData llrpServiceInstanceRuntimeData;
	private RFCRuntimeData rfcRuntimeData;
	private GPIORuntimeData gpioRuntimeData;

	public void setRuntimeData(LLRPServiceInstanceRuntimeData llrpServiceInstanceRuntimeData,
			LLRPRuntimeData llrpRuntimeData, RFCRuntimeData rfcRuntimeData,
			GPIORuntimeData gpioRuntimeData) {
		setRuntimeData(llrpRuntimeData);
		this.llrpServiceInstanceRuntimeData = llrpServiceInstanceRuntimeData;
		this.rfcRuntimeData = rfcRuntimeData;
		this.gpioRuntimeData = gpioRuntimeData;
	}

	public LLRPServiceInstanceRuntimeData getLLRPServiceInstanceRuntimeData() {
		return llrpServiceInstanceRuntimeData;
	}

	public RFCRuntimeData getRFCRuntimeData() {
		return rfcRuntimeData;
	}

	public GPIORuntimeData getGPIORuntimeData() {
		return gpioRuntimeData;
	}

	@Override
	public String toString() {
		return "FSMLLRPSetReaderConfigEvent [llrpServiceInstanceRuntimeData="
				+ llrpServiceInstanceRuntimeData + ", rfcRuntimeData=" + rfcRuntimeData
				+ ", gpioRuntimeData=" + gpioRuntimeData + ", " + super.toString() + "]";
	}
}