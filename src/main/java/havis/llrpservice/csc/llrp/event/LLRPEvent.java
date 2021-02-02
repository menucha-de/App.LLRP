package havis.llrpservice.csc.llrp.event;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class LLRPEvent {
	private ServerSocketChannel serverChannel;
	private SocketChannel channel;

	public LLRPEvent(ServerSocketChannel serverChannel, SocketChannel channel) {
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