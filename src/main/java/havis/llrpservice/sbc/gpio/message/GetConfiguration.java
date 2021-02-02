package havis.llrpservice.sbc.gpio.message;

import havis.device.io.Type;

import java.util.List;

public class GetConfiguration extends Request {

	private final List<Type> types;
	private final short pinId;

	public GetConfiguration(MessageHeader messageHeader, List<Type> types,
			short pinId) {
		super(messageHeader, MessageType.GET_CONFIGURATION);
		this.types = types;
		this.pinId = pinId;
	}

	public List<Type> getTypes() {
		return types;
	}

	public short getPinId() {
		return pinId;
	}

	@Override
	public String toString() {
		return "GetConfiguration [types=" + types + ", pinId=" + pinId + ", "
				+ super.toString() + "]";
	}
}
