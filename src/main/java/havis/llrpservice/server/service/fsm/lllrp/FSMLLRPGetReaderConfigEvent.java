package havis.llrpservice.server.service.fsm.lllrp;

import havis.llrpservice.server.service.fsm.gpio.GPIORuntimeData;
import havis.llrpservice.server.service.fsm.rfc.RFCRuntimeData;

public class FSMLLRPGetReaderConfigEvent extends FSMLLRPEvent {

	private RFCRuntimeData rfcRuntimeData;
	private GPIORuntimeData gpioRuntimeData;

	public void setRuntimeData(LLRPRuntimeData llrpRuntimeData,
			RFCRuntimeData rfcRuntimeData, GPIORuntimeData gpioRuntimeData) {
		setRuntimeData(llrpRuntimeData);
		this.rfcRuntimeData = rfcRuntimeData;
		this.gpioRuntimeData = gpioRuntimeData;
	}

	public RFCRuntimeData getRFCRuntimeData() {
		return rfcRuntimeData;
	}

	public GPIORuntimeData getGPIORuntimeData() {
		return gpioRuntimeData;
	}

	@Override
	public String toString() {
		return "FSMLLRPGetReaderConfigEvent [rfcRuntimeData=" + rfcRuntimeData
				+ ", gpioRuntimeData=" + gpioRuntimeData + ", "
				+ super.toString() + "]";
	}
}