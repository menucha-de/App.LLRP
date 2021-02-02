package havis.llrpservice.common.tcp;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

/**
 * This class wraps methods of {@link ServerSocketChannel}.
 * <p>
 * The functionality of these methods can be mocked with frameworks like JMockit
 * if this wrapper is used around the real implementation.
 * </p>
 */
class ServerSocketChannelWrapper {

	private final ServerSocketChannel serverChannel;

	ServerSocketChannelWrapper(ServerSocketChannel serverChannel) {
		this.serverChannel = serverChannel;
	}

	SocketChannel accept() throws IOException {
		return serverChannel.accept();
	}
}
