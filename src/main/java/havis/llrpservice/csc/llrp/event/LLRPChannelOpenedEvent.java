package havis.llrpservice.csc.llrp.event;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class LLRPChannelOpenedEvent extends LLRPEvent {

	public LLRPChannelOpenedEvent(ServerSocketChannel serverChannel,
			SocketChannel channel) {
		super(serverChannel, channel);
	}
}