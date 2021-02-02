package havis.llrpservice.server.service.fsm.lllrp;

import havis.llrpservice.data.message.Message;

public class FSMLLRPMessageEvent extends FSMLLRPEvent {

	private Message message;

	public Message getMessage() {
		return message;
	}

	public void setMessage(Message msg) {
		this.message = msg;
	}

	@Override
	public String toString() {
		return "FSMLLRPMessageEvent [message=" + message + ", "
				+ super.toString() + "]";
	}
}