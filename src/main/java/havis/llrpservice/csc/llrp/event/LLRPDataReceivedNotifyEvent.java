package havis.llrpservice.csc.llrp.event;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class LLRPDataReceivedNotifyEvent extends LLRPEvent {

	public LLRPDataReceivedNotifyEvent(ServerSocketChannel serverChannel,
			SocketChannel channel) {
		super(serverChannel, channel);
	}
}