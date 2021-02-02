package havis.llrpservice.sbc.rfc.message;

import java.io.Serializable;

public class MessageHeader implements Serializable {

	private static final long serialVersionUID = 2952143733407421598L;

	private MessageType messageType;
	private long id;

	public MessageHeader() {
	}

	public MessageHeader(long id) {
		this.id = id;
	}

	@Override
	public String toString() {
		return "MessageHeader [messageType=" + messageType + ", id=" + id + "]";
	}

	/**
	 * Returns the message type. It is set when the root DTO of the message is
	 * instantiated.
	 * 
	 * @return The message type
	 */
	public MessageType getMessageType() {
		return messageType;
	}

	public void setMessageType(MessageType type) {
		this.messageType = type;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}
}
