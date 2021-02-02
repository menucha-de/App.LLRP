package havis.llrpservice.server.service.fsm.lllrp;

import java.util.List;

import havis.device.io.Type;
import havis.device.rf.capabilities.CapabilityType;
import havis.llrpservice.server.service.fsm.LLRPServiceInstanceRuntimeData;
import havis.llrpservice.server.service.fsm.gpio.GPIORuntimeData;
import havis.llrpservice.server.service.fsm.rfc.RFCRuntimeData;

public class FSMLLRPGetReaderCapabilitiesEvent extends FSMLLRPEvent {

	private LLRPServiceInstanceRuntimeData llrpServiceInstanceRuntimeData;
	private RFCRuntimeData rfcRuntimeData;
	private GPIORuntimeData gpioRuntimeData;
	private List<CapabilityType> rfcRequest;
	private List<Type> gpioRequest;

	public void setRuntimeData(LLRPServiceInstanceRuntimeData llrpServiceInstanceRuntimeData,
			LLRPRuntimeData llrpRuntimeData, RFCRuntimeData rfcRuntimeData,
			GPIORuntimeData gpioRuntimeData) {
		this.llrpServiceInstanceRuntimeData = llrpServiceInstanceRuntimeData;
		setRuntimeData(llrpRuntimeData);
		this.rfcRuntimeData = rfcRuntimeData;
		this.gpioRuntimeData = gpioRuntimeData;
		rfcRequest = null;
		gpioRequest = null;
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

	public List<CapabilityType> getRFCRequest() {
		return rfcRequest;
	}

	public void setRFCRequest(List<CapabilityType> rfcRequest) {
		this.rfcRequest = rfcRequest;
	}

	public List<Type> getGPIORequest() {
		return gpioRequest;
	}

	public void setGPIORequest(List<Type> gpioRequest) {
		this.gpioRequest = gpioRequest;
	}

	@Override
	public String toString() {
		return "FSMLLRPGetReaderCapabilitiesEvent [llrpServiceInstanceRuntimeData="
				+ llrpServiceInstanceRuntimeData + ", rfcRuntimeData=" + rfcRuntimeData
				+ ", gpioRuntimeData=" + gpioRuntimeData + ", rfcRequest=" + rfcRequest
				+ ", gpioRequest=" + gpioRequest + ", " + super.toString() + "]";
	}
}
