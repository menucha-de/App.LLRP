package havis.llrpservice.sbc.gpio.message;

public class Response implements Message {
	private MessageHeader messageHeader;
	private Exception exception;

	public Response(MessageHeader messageHeader, MessageType type) {
		this.messageHeader = messageHeader;
		this.messageHeader.setMessageType(type);
	}

	@Override
	public MessageHeader getMessageHeader() {
		return messageHeader;
	}

	public Exception getException() {
		return exception;
	}

	public void setException(Exception exception) {
		this.exception = exception;
	}

	@Override
	public String toString() {
		return "Response [messageHeader=" + messageHeader + ", exception="
				+ exception + "]";
	}
}
