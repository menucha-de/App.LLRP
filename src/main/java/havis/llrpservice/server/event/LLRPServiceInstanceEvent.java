package havis.llrpservice.server.event;

public class LLRPServiceInstanceEvent implements Event {

	public enum MessageType {
		LLRP_SERVER_OPENED,
		LLRP_SERVER_CLOSED, 
		LLRP_CLIENT_OPENED, 
		LLRP_CLIENT_CLOSED, 
		LLRP_DATA_SENT,
		
		RFC_CLIENT_OPENED,
		RFC_CLIENT_CLOSED, 
		
		GPIO_CLIENT_OPENED,
		GPIO_CLIENT_CLOSED,
		
		CANCEL
	}

	private final MessageType messageType;
	private Throwable exception;

	public LLRPServiceInstanceEvent(MessageType messageType, Throwable exception) {
		this.messageType = messageType;
		this.exception = exception;
	}

	@Override
	public EventType getEventType() {
		return EventType.INSTANCE_EVENT;
	}

	public Throwable getException() {
		return exception;
	}

	public MessageType getMessageType() {
		return messageType;
	}

	@Override
	public String toString() {
		return "LLRPServiceInstanceEvent [messageType=" + messageType
				+ ", exception=" + exception + "]";
	}
}
