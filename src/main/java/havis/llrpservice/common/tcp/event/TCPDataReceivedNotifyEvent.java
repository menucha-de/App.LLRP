package havis.llrpservice.common.tcp.event;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class TCPDataReceivedNotifyEvent extends TCPEvent {

	public TCPDataReceivedNotifyEvent(ServerSocketChannel serverChannel,
			SocketChannel channel) {
		super(serverChannel, channel);
	}
}