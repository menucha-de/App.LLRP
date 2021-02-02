package havis.llrpservice.sbc.rfc.event;

import havis.llrpservice.sbc.rfc.RFCException;
import havis.llrpservice.sbc.rfc.message.Message;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;

public class RFCChannelClosedEvent extends RFCEvent {
	private List<Message> pendingSendingData;
	private List<Message> pendingReceivedData;
	private RFCException exception;

	public RFCChannelClosedEvent(ServerSocketChannel serverChannel,
			SocketChannel channel, List<Message> pendingSendingData,
			List<Message> pendingReceivedData, RFCException exception) {
		super(serverChannel, channel);
		this.pendingSendingData = pendingSendingData;
		this.pendingReceivedData = pendingReceivedData;
		this.exception = exception;
	}

	/**
	 * Returns the pending data for sending.
	 * <p>
	 * A byte buffer cannot be returned because the RF controller API is used
	 * instead of sockets and no serializer exists for RFC messages.
	 * </p>
	 * 
	 * @return pending data
	 */
	public List<Message> getPendingSendingData() {
		return pendingSendingData;
	}

	/**
	 * Returns the received pending data.
	 * <p>
	 * A byte buffer cannot be returned because the RF controller API is used
	 * instead of sockets and no serializer exists for RFC messages.
	 * </p>
	 * 
	 * @return pending data
	 */
	public List<Message> getPendingReceivedData() {
		return pendingReceivedData;
	}

	public RFCException getException() {
		return exception;
	}
}