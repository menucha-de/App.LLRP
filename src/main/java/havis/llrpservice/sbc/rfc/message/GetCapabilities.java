package havis.llrpservice.sbc.rfc.message;

import java.util.List;
import havis.device.rf.capabilities.CapabilityType;

public class GetCapabilities extends Request {

	private final List<CapabilityType> types;

	public GetCapabilities(MessageHeader messageHeader,
			List<CapabilityType> types) {
		super(messageHeader, MessageType.GET_CAPABILITIES);
		this.types = types;
	}

	public List<CapabilityType> getTypes() {
		return types;
	}

	@Override
	public String toString() {
		return "GetCapabilities [types=" + types + ", " + super.toString()
				+ "]";
	}
}
