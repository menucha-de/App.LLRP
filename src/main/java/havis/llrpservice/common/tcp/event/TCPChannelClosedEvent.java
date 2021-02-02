package havis.llrpservice.common.tcp.event;

import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class TCPChannelClosedEvent extends TCPEvent {		
	private ByteBuffer pendingSendingData;
	private ByteBuffer pendingReceivedData;
	private Throwable exception;

	public TCPChannelClosedEvent(ServerSocketChannel serverChannel,
			SocketChannel channel, ByteBuffer pendingSendingData,
			ByteBuffer pendingReceivedData, Throwable exception) {
		super(serverChannel, channel);
		this.pendingSendingData = pendingSendingData;
		this.pendingReceivedData = pendingReceivedData;
		this.exception = exception;
	}

	/**
	 * @return pending data (ready to read)
	 */
	public ByteBuffer getPendingSendingData() {
		return pendingSendingData;
	}

	/**
	 * @return pending data (ready to read)
	 */
	public ByteBuffer getPendingReceivedData() {
		return pendingReceivedData;
	}

	public Throwable getException() {
		return exception;
	}
}