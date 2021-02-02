package havis.llrpservice.server.service.fsm.gpio;

import havis.llrpservice.sbc.gpio.message.Message;

public class FSMGPIOMessageEvent extends FSMGPIOEvent {

	private Message message;

	public Message getMessage() {
		return message;
	}

	public void setMessage(Message msg) {
		this.message = msg;
	}

	@Override
	public String toString() {
		return "FSMGPIOMessageEvent [message=" + message + ", "
				+ super.toString() + "]";
	}
}