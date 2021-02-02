package havis.llrpservice.sbc.gpio.message;

public class ConnectionAttempted implements Message {

	private final MessageHeader messageHeader;

	public ConnectionAttempted(MessageHeader messageHeader) {
		this.messageHeader = messageHeader;
		this.messageHeader.setMessageType(MessageType.CONNECTION_ATTEMPTED);
	}

	@Override
	public MessageHeader getMessageHeader() {
		return messageHeader;
	}

	@Override
	public String toString() {
		return "ConnectionAttempted [messageHeader=" + messageHeader + "]";
	}
}
