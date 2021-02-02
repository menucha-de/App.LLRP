package havis.llrpservice.server.service.fsm.gpio;

import havis.llrpservice.sbc.gpio.message.Message;
import havis.llrpservice.server.gpio.GPIOMessageHandler;
import havis.llrpservice.server.service.messageHandling.GPIOMessageCreator;

import java.util.ArrayList;
import java.util.List;

public class GPIORuntimeData {

	private final GPIOMessageCreator messageCreator = new GPIOMessageCreator();
	private final GPIOMessageHandler messageHandler;
	/**
	 * The currently processed messages (while a GPIO request is processed GPIO
	 * messages like GPI_EVENT or CONNECTION_ATTEMPTED can be received).
	 */
	private final List<Message> currentMessages = new ArrayList<>();
	/**
	 * Whether a GPIO message is expected to be received. The flag is reset
	 * after the message has been received.
	 */
	private boolean isMessageExpected = false;

	public GPIORuntimeData(GPIOMessageHandler messageHandler) {
		this.messageHandler = messageHandler;
	}

	public GPIOMessageCreator getMessageCreator() {
		return messageCreator;
	}

	public GPIOMessageHandler getMessageHandler() {
		return messageHandler;
	}

	public List<Message> getCurrentMessages() {
		return currentMessages;
	}

	public Message getCurrentMessage() {
		int size = currentMessages.size();
		return size == 0 ? null : currentMessages.get(size - 1);
	}

	public boolean isMessageExpected() {
		return isMessageExpected;
	}

	public void setMessageExpected(boolean isExpected) {
		this.isMessageExpected = isExpected;
	}

	@Override
	public String toString() {
		return "GPIORuntimeData [messageCreator=" + messageCreator
				+ ", messageHandler=" + messageHandler + ", currentMessages="
				+ currentMessages + ", isMessageExpected=" + isMessageExpected
				+ "]";
	}
}