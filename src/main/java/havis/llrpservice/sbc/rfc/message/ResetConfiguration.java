package havis.llrpservice.sbc.rfc.message;

public class ResetConfiguration extends Request {

	public ResetConfiguration(MessageHeader messageHeader) {
		super(messageHeader, MessageType.RESET_CONFIGURATION);
	}

	@Override
	public String toString() {
		return "ResetConfiguration [" + super.toString() + "]";
	}
}
