package havis.llrpservice.sbc.rfc.event;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class RFCChannelOpenedEvent extends RFCEvent {

	public RFCChannelOpenedEvent(ServerSocketChannel serverChannel,
			SocketChannel channel) {
		super(serverChannel, channel);
	}
}