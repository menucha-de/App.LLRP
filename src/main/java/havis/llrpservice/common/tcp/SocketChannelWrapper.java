package havis.llrpservice.common.tcp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * This class wraps methods of {@link SocketChannel}.
 * <p>
 * The functionality of these methods can be mocked with frameworks like JMockit
 * if this wrapper is used around the real implementation.
 * </p>
 */
class SocketChannelWrapper {

	private final SocketChannel channel;

	SocketChannelWrapper(SocketChannel channel) {
		this.channel = channel;
	}

	boolean finishConnect() throws IOException {
		return channel.finishConnect();
	}

	int read(ByteBuffer dst) throws IOException {
		return channel.read(dst);
	}

	int write(ByteBuffer src) throws IOException {
		return channel.write(src);
	}
}
