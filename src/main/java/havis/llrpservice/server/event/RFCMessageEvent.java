package havis.llrpservice.server.event;

import havis.llrpservice.sbc.rfc.message.Message;

public class RFCMessageEvent implements Event {

	private final Message message;
	private Object data;

	public RFCMessageEvent(Message message) {
		this(message, null /* data */);
	}

	public RFCMessageEvent(Message message, Object data) {
		this.message = message;
		this.data = data;
	}

	@Override
	public EventType getEventType() {
		return EventType.RFC_MESSAGE;
	}

	public Message getMessage() {
		return message;
	}

	public Object getData() {
		return data;
	}

	@Override
	public String toString() {
		return "RFCMessageEvent [message=" + message + ", data=" + data + "]";
	}

}
