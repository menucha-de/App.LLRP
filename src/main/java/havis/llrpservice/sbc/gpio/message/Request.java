package havis.llrpservice.sbc.gpio.message;

public class Request implements Message {
	private final MessageHeader messageHeader;

	Request(MessageHeader messageHeader, MessageType type) {
		this.messageHeader = messageHeader;
		this.messageHeader.setMessageType(type);
	}

	@Override
	public MessageHeader getMessageHeader() {
		return messageHeader;
	}

	@Override
	public String toString() {
		return "Request [messageHeader=" + messageHeader + "]";
	}
}
