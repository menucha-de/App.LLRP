package havis.llrpservice.csc.llrp;

import havis.llrpservice.csc.llrp.event.LLRPChannelClosedEvent;
import havis.llrpservice.csc.llrp.event.LLRPChannelOpenedEvent;
import havis.llrpservice.csc.llrp.event.LLRPDataReceivedNotifyEvent;
import havis.llrpservice.csc.llrp.event.LLRPDataSentEvent;
import havis.llrpservice.data.message.MessageHeader;
import havis.llrpservice.data.message.serializer.ByteBufferSerializer;
import havis.llrpservice.common.tcp.TCPClientMultiplexed;
import havis.llrpservice.common.tcp.TCPEventHandler;
import havis.llrpservice.common.tcp.event.TCPChannelClosedEvent;
import havis.llrpservice.common.tcp.event.TCPChannelOpenedEvent;
import havis.llrpservice.common.tcp.event.TCPDataReceivedNotifyEvent;
import havis.llrpservice.common.tcp.event.TCPDataSentEvent;

import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This TCP event handler receives the events from a TCP client/server, converts
 * the TCP events to LLRP events and sends them to an LLRP event handler.
 * <p>
 * It can be registered as TCP event handler while opening a TCP channel with
 * eg.
 * {@link TCPClientMultiplexed#requestOpeningChannel(java.net.InetAddress, int, TCPEventHandler)}
 * .
 * </p>
 */
class TCP2LLRPEventHandler implements TCPEventHandler {

	private final static Logger log = Logger.getLogger(TCP2LLRPEventHandler.class.getName());

	private ByteBufferSerializer serializer = new ByteBufferSerializer();
	private LLRPEventHandler eventHandler;

	TCP2LLRPEventHandler(LLRPEventHandler eventHandler) {
		this.eventHandler = eventHandler;
	}

	@Override
	public void channelOpened(TCPChannelOpenedEvent event) {
		ServerSocketChannel serverChannel = event.getServerChannel();
		SocketChannel channel = event.getChannel();
		if (log.isLoggable(Level.INFO)) {
			log.log(Level.INFO, "{0}: Opened", (channel == null ? serverChannel.toString() : channel.toString()));
		}
		// delegate event to event handler
		eventHandler.channelOpened(new LLRPChannelOpenedEvent(serverChannel,
				channel));
	}

	@Override
	public void dataSent(TCPDataSentEvent event) {
		SocketChannel channel = event.getChannel();
		ByteBuffer eventData = event.getData();
		Long messageId = null;
		Throwable exception = null;
		ByteBuffer pendingData = null;
		// serialize message
		try {
			// get the message identifier
			// (the event contains all data for a message because only
			// whole messages are sent)
			eventData.rewind();
			MessageHeader messageHeader = serializer
					.deserializeMessageHeader(eventData);
			messageId = messageHeader.getId();
			if (log.isLoggable(Level.INFO)) {
				log.log(Level.INFO, "{0}: Sent {1} (id={2})", new Object[]{ channel.toString(), messageHeader.getMessageType(), messageId });
			}
		} catch (Throwable t) {
			exception = t;
			eventData.rewind();
			pendingData = eventData;
		}
		// create LLRP event and fire it
		eventHandler.dataSent(new LLRPDataSentEvent(event.getServerChannel(),
				channel, messageId, pendingData, exception));
	}

	@Override
	public void dataReceived(TCPDataReceivedNotifyEvent event) {
		// delegate event to event handler
		eventHandler.dataReceived(new LLRPDataReceivedNotifyEvent(event
				.getServerChannel(), event.getChannel()));
	}

	@Override
	public void channelClosed(TCPChannelClosedEvent event) {
		ServerSocketChannel serverChannel = event.getServerChannel();
		SocketChannel channel = event.getChannel();
		if (log.isLoggable(Level.INFO)) {
			log.log(Level.INFO, "{0}: Closed", (channel == null ? serverChannel.toString() : channel.toString()));
		}
		// delegate event to event handler
		eventHandler.channelClosed(new LLRPChannelClosedEvent(serverChannel,
				channel, event.getPendingSendingData(), event
						.getPendingReceivedData(), event.getException()));
	}
}
