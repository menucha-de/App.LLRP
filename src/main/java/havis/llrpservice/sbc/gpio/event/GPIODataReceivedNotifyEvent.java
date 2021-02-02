package havis.llrpservice.sbc.gpio.event;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class GPIODataReceivedNotifyEvent extends GPIOEvent {

	public GPIODataReceivedNotifyEvent(ServerSocketChannel serverChannel,
			SocketChannel channel) {
		super(serverChannel, channel);
	}
}