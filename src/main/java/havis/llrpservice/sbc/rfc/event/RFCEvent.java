package havis.llrpservice.sbc.rfc.event;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class RFCEvent {
	private ServerSocketChannel serverChannel;
	private SocketChannel channel;

	public RFCEvent(ServerSocketChannel serverChannel, SocketChannel channel) {
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