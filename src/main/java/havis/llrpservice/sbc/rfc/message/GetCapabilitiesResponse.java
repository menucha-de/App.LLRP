package havis.llrpservice.sbc.rfc.message;

import havis.device.rf.capabilities.Capabilities;

import java.util.List;

public class GetCapabilitiesResponse extends Response {

	private List<Capabilities> capabilities;

	public GetCapabilitiesResponse() {

	}

	public GetCapabilitiesResponse(MessageHeader messageHeader,
			List<Capabilities> capabilities) {
		super(messageHeader, MessageType.GET_CAPABILITIES_RESPONSE);
		this.capabilities = capabilities;
	}

	public List<Capabilities> getCapabilities() {
		return capabilities;
	}

	@Override
	public String toString() {
		return "GetCapabilitiesResponse [capabilities=" + capabilities + ", "
				+ super.toString() + "]";
	}
}
