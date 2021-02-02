package havis.llrpservice.common.tcp.event;

import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class TCPDataSentEvent extends TCPEvent {
	private ByteBuffer data;

	public TCPDataSentEvent(ServerSocketChannel serverChannel,
			SocketChannel channel, ByteBuffer data) {
		super(serverChannel, channel);
		this.data = data;
	}

	/**
	 * @return the data buffer with the current position at the end of the
	 *         buffer (all remaining data has been written).
	 */
	public ByteBuffer getData() {
		return data;
	}
}