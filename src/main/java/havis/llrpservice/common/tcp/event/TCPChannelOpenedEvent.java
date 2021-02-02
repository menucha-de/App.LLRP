package havis.llrpservice.common.tcp.event;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class TCPChannelOpenedEvent extends TCPEvent {

	public TCPChannelOpenedEvent(ServerSocketChannel serverChannel,
			SocketChannel channel) {
		super(serverChannel, channel);
	}
}