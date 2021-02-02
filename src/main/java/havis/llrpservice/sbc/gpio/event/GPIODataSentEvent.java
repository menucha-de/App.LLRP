package havis.llrpservice.sbc.gpio.event;

import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class GPIODataSentEvent extends GPIOEvent {
	private Long messageId;
	private ByteBuffer pendingData;
	private Throwable exception;

	public GPIODataSentEvent(ServerSocketChannel serverChannel,
			SocketChannel channel, Long messageId, ByteBuffer pendingData,
			Throwable exception) {
		super(serverChannel, channel);
		this.messageId = messageId;
		this.pendingData = pendingData;
		this.exception = exception;
	}

	/**
	 * Returns the identifier of the sent message.
	 * <p>
	 * If an exception occurs while the message identifier is being determined
	 * then the available message data are provided via
	 * {@link #getPendingData()}.
	 * </p>
	 * 
	 * @return The message identifier
	 */
	public Long getMessageId() {
		return messageId;
	}

	/**
	 * Returns the available message data if the deserialization of the message
	 * failed.
	 * 
	 * @return pending messages
	 */
	public ByteBuffer getPendingData() {
		return pendingData;
	}

	/**
	 * Returns an exception if the deserialization of the message failed. The
	 * available message data are provided via {@link #getPendingData()}.
	 * 
	 * @return The exception
	 */
	public Throwable getException() {
		return exception;
	}
}