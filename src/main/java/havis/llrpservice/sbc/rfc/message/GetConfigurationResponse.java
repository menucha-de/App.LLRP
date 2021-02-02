package havis.llrpservice.sbc.rfc.message;

import havis.device.rf.configuration.Configuration;

import java.util.List;

public class GetConfigurationResponse extends Response {

	private List<Configuration> configuration;

	public GetConfigurationResponse() {
	}

	public GetConfigurationResponse(MessageHeader messageHeader,
			List<Configuration> configuration) {
		super(messageHeader, MessageType.GET_CONFIGURATION_RESPONSE);
		this.configuration = configuration;
	}

	public List<Configuration> getConfiguration() {
		return configuration;
	}

	@Override
	public String toString() {
		return "GetConfigurationResponse [configuration=" + configuration
				+ ", " + super.toString() + "]";
	}
}
