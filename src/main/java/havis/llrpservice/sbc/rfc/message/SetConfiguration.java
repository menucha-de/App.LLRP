package havis.llrpservice.sbc.rfc.message;

import java.util.List;

import havis.device.rf.configuration.Configuration;

public class SetConfiguration extends Request {

	private final List<Configuration> configuration;

	public SetConfiguration(MessageHeader messageHeader,
			List<Configuration> configuration) {
		super(messageHeader, MessageType.SET_CONFIGURATION);
		this.configuration = configuration;
	}

	public List<Configuration> getConfiguration() {
		return configuration;
	}

	@Override
	public String toString() {
		return "SetConfiguration [configuration=" + configuration + ", "
				+ super.toString() + "]";
	}
}
