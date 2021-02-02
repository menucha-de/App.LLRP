package havis.llrpservice.server.event;

import havis.llrpservice.data.message.Message;

public class LLRPMessageEvent implements Event {

	private final Message message;

	public LLRPMessageEvent(Message message) {
		this.message = message;
	}

	@Override
	public EventType getEventType() {
		return EventType.LLRP_MESSAGE;
	}

	public Message getMessage() {
		return message;
	}

	@Override
	public String toString() {
		return "LLRPMessageEvent [message=" + message + "]";
	}
}
