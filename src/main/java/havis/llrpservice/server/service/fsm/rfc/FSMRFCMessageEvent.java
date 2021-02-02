package havis.llrpservice.server.service.fsm.rfc;

import havis.llrpservice.sbc.rfc.message.Message;

public class FSMRFCMessageEvent extends FSMRFCEvent {

	private Message message;
	private Object messageData;

	public Message getMessage() {
		return message;
	}

	public void setMessage(Message msg) {
		this.message = msg;
	}

	public void setMessageData(Object messageData) {
		this.messageData = messageData;
	}

	public Object getMessageData() {
		return messageData;
	}

	@Override
	public String toString() {
		return "FSMRFCMessageEvent [message=" + message + ", " + super.toString() + "]";
	}
}