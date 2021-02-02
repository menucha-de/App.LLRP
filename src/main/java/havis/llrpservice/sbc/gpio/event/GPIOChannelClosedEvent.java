package havis.llrpservice.sbc.gpio.event;

import havis.llrpservice.sbc.gpio.GPIOException;
import havis.llrpservice.sbc.gpio.message.Message;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.List;

public class GPIOChannelClosedEvent extends GPIOEvent {
	private List<Message> pendingSendingData;
	private List<Message> pendingReceivedData;
	private GPIOException exception;

	public GPIOChannelClosedEvent(ServerSocketChannel serverChannel,
			SocketChannel channel, List<Message> pendingSendingData,
			List<Message> pendingReceivedData, GPIOException exception) {
		super(serverChannel, channel);
		this.pendingSendingData = pendingSendingData;
		this.pendingReceivedData = pendingReceivedData;
		this.exception = exception;
	}

	/**
	 * Returns the pending data for sending.
	 * <p>
	 * A byte buffer cannot be returned because the GPIO controller API is used
	 * instead of sockets and no serializer exists for GPIO messages.
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
	 * A byte buffer cannot be returned because the GPIO controller API is used
	 * instead of sockets and no serializer exists for GPIO messages.
	 * </p>
	 * 
	 * @return pending data
	 */
	public List<Message> getPendingReceivedData() {
		return pendingReceivedData;
	}

	public GPIOException getException() {
		return exception;
	}
}