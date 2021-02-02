package havis.llrpservice.sbc.gpio.event;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class GPIOEvent {
	private ServerSocketChannel serverChannel;
	private SocketChannel channel;

	public GPIOEvent(ServerSocketChannel serverChannel, SocketChannel channel) {
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