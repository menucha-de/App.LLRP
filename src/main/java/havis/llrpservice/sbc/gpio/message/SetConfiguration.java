package havis.llrpservice.sbc.gpio.message;

import havis.device.io.Configuration;

import java.util.List;

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
