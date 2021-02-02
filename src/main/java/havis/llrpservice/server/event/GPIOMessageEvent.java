package havis.llrpservice.server.event;

import havis.llrpservice.sbc.gpio.message.Message;

public class GPIOMessageEvent implements Event {

	private final Message message;

	public GPIOMessageEvent(Message message) {
		this.message = message;
	}

	@Override
	public EventType getEventType() {
		return EventType.GPIO_MESSAGE;
	}

	public Message getMessage() {
		return message;
	}

	@Override
	public String toString() {
		return "GPIOMessageEvent [message=" + message + "]";
	}
}
