package havis.llrpservice.sbc.gpio.message;

public class SetConfigurationResponse extends Response {

	public SetConfigurationResponse(MessageHeader messageHeader) {
		super(messageHeader, MessageType.SET_CONFIGURATION_RESPONSE);
	}

	@Override
	public String toString() {
		return "SetConfigurationResponse [" + super.toString() + "]";
	}
}
