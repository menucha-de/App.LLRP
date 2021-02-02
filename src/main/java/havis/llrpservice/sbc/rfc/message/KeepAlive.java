package havis.llrpservice.sbc.rfc.message;

public class KeepAlive implements Message {

	private final MessageHeader messageHeader;

	public KeepAlive(MessageHeader messageHeader) {
		this.messageHeader = messageHeader;
		this.messageHeader.setMessageType(MessageType.KEEP_ALIVE);
	}

	@Override
	public MessageHeader getMessageHeader() {
		return messageHeader;
	}

	@Override
	public String toString() {
		return "KeepAlive [messageHeader=" + messageHeader + "]";
	}
}
