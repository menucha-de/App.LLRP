package havis.llrpservice.common.tcp.event;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class TCPEvent {
	private ServerSocketChannel serverChannel;
	private SocketChannel channel;

	public TCPEvent(ServerSocketChannel serverChannel,
			SocketChannel channel) {
		this.serverChannel = serverChannel;
		this.channel = channel;
	}

	public ServerSocketChannel getServerChannel() {
		return serverChannel;
	}

	public SocketChannel getChannel() {
		return channel;
	}
}