package havis.llrpservice.sbc.rfc.message;

import java.util.List;

import havis.device.rf.configuration.ConfigurationType;

public class GetConfiguration extends Request {

	private final List<ConfigurationType> types;
	private final short antennaID;

	public GetConfiguration(MessageHeader messageHeader,
			List<ConfigurationType> types, short antennaID) {
		super(messageHeader, MessageType.GET_CONFIGURATION);
		this.types = types;
		this.antennaID = antennaID;
	}

	public List<ConfigurationType> getTypes() {
		return types;
	}

	public short getAntennaID() {
		return antennaID;
	}

	@Override
	public String toString() {
		return "GetConfiguration [types=" + types + ", antennaID=" + antennaID
				+ ", " + super.toString() + "]";
	}
}
