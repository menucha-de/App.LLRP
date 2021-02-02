package havis.llrpservice.sbc.gpio.event;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class GPIOChannelOpenedEvent extends GPIOEvent {

	public GPIOChannelOpenedEvent(ServerSocketChannel serverChannel,
			SocketChannel channel) {
		super(serverChannel, channel);
	}
}