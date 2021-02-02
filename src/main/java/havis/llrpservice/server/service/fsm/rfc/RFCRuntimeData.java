package havis.llrpservice.server.service.fsm.rfc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import havis.llrpservice.sbc.rfc.message.Message;
import havis.llrpservice.server.rfc.RFCMessageHandler;
import havis.llrpservice.server.service.messageHandling.RFCMessageCreator;

public class RFCRuntimeData {

	private final RFCMessageCreator messageCreator = new RFCMessageCreator();
	private final RFCMessageHandler messageHandler;
	/**
	 * The currently processed messages (while a RFC request is processed RFC
	 * messages like CONNECTION_ATTEMPTED can be received).
	 */
	private final List<Message> currentMessages = new ArrayList<>();
	/**
	 * MessageId -&gt; message data
	 */
	private final Map<Long, Object> messageData = new HashMap<>();
	/**
	 * Whether a RFC message is expected to be received. The flag is reset after
	 * the message has been received.
	 */
	private boolean isMessageExpected = false;

	public RFCRuntimeData(RFCMessageHandler messageHandler) {
		this.messageHandler = messageHandler;
	}

	public RFCMessageCreator getMessageCreator() {
		return messageCreator;
	}

	public RFCMessageHandler getMessageHandler() {
		return messageHandler;
	}

	public List<Message> getCurrentMessages() {
		return currentMessages;
	}

	public Message getCurrentMessage() {
		int size = currentMessages.size();
		return size == 0 ? null : currentMessages.get(size - 1);
	}

	public Map<Long, Object> getMessageData() {
		return messageData;
	}

	public boolean isMessageExpected() {
		return isMessageExpected;
	}

	public void setMessageExpected(boolean isExpected) {
		this.isMessageExpected = isExpected;
	}

	@Override
	public String toString() {
		return "RFCRuntimeData [messageCreator=" + messageCreator + ", messageHandler="
				+ messageHandler + ", currentMessages=" + currentMessages + ", messageData="
				+ messageData + ", isMessageExpected=" + isMessageExpected + "]";
	}
}