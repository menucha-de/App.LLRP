package havis.llrpservice.sbc.rfc.event;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class RFCDataReceivedNotifyEvent extends RFCEvent {

	public RFCDataReceivedNotifyEvent(ServerSocketChannel serverChannel,
			SocketChannel channel) {
		super(serverChannel, channel);
	}
}