package havis.llrpservice.sbc.gpio.message;

import havis.device.io.StateEvent;

public class StateChanged implements Message {

	private final MessageHeader messageHeader;
	private StateEvent stateEvent;

	public StateChanged(MessageHeader messageHeader, StateEvent stateEvent) {
		this.messageHeader = messageHeader;
		this.messageHeader.setMessageType(MessageType.STATE_CHANGED);
		this.stateEvent = stateEvent;
	}

	@Override
	public MessageHeader getMessageHeader() {
		return messageHeader;
	}

	public StateEvent getStateEvent() {
		return stateEvent;
	}

	@Override
	public String toString() {
		return "StateChanged [messageHeader=" + messageHeader + ", stateEvent="
				+ stateEvent + "]";
	}
}
