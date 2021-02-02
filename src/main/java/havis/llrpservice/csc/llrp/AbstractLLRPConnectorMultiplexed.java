package havis.llrpservice.csc.llrp;

import havis.llrpservice.common.serializer.JsonSerializer;
import havis.llrpservice.common.tcp.TCPConnectorStoppedException;
import havis.llrpservice.common.tcp.TCPTimeoutException;
import havis.llrpservice.common.tcp.TCPUnknownChannelException;
import havis.llrpservice.csc.llrp.event.LLRPChannelClosedEvent;
import havis.llrpservice.csc.llrp.event.LLRPChannelOpenedEvent;
import havis.llrpservice.csc.llrp.event.LLRPDataReceivedNotifyEvent;
import havis.llrpservice.csc.llrp.event.LLRPDataSentEvent;
import havis.llrpservice.csc.llrp.json.LLRPJacksonMixIns;
import havis.llrpservice.data.message.Message;
import havis.llrpservice.data.message.MessageHeader;
import havis.llrpservice.data.message.parameter.serializer.InvalidParameterTypeException;
import havis.llrpservice.data.message.serializer.ByteBufferSerializer;
import havis.llrpservice.data.message.serializer.InvalidMessageTypeException;
import havis.llrpservice.data.message.serializer.InvalidProtocolVersionException;

import java.io.IOException;
import java.lang.Thread.State;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The base class for the multiplexed LLRP server and client classes
 * {@link LLRPServerMultiplexed}, {@link LLRPClientMultiplexed}.
 */
abstract class AbstractLLRPConnectorMultiplexed {

	private final static Logger log = Logger
			.getLogger(AbstractLLRPConnectorMultiplexed.class.getName());

	public static final int NO_TIMEOUT = -1;
	public static final int RETURN_IMMEDIATELY = 0;

	private final Object dataLock = new Object();
	private LLRPMessageCreator messageCreator = new LLRPMessageCreator();
	private Map<SelectableChannel, List<ByteBuffer>> receivedData = new HashMap<>();

	class LLRPEventHandlerBridge implements LLRPEventHandler {

		private final LLRPEventHandler eventHandler;

		LLRPEventHandlerBridge(LLRPEventHandler eventHandler) {
			this.eventHandler = eventHandler;
		}

		@Override
		public void channelOpened(LLRPChannelOpenedEvent event) {
			SocketChannel channel = event.getChannel();
			if (channel != null) {
				// create an empty list for received data to enable awaiting of
				// the data (method awaitReceivedData)
				synchronized (dataLock) {
					receivedData.put(channel, new ArrayList<ByteBuffer>());
				}
			}
			eventHandler.channelOpened(event);
		}

		@Override
		public void dataSent(LLRPDataSentEvent event) {
			eventHandler.dataSent(event);
		}

		@Override
		public void dataReceived(LLRPDataReceivedNotifyEvent event) {
			eventHandler.dataReceived(event);
		}

		@Override
		public void channelClosed(LLRPChannelClosedEvent event) {
			SocketChannel channel = event.getChannel();
			if (channel != null) {
				// get existing data for the LLRP message from the message
				// creator and put them back to the list of the received data
				ByteBuffer llrpData = null;
				List<ByteBuffer> data = null;
				synchronized (dataLock) {
					llrpData = messageCreator.remove(channel);
					data = receivedData.remove(channel);
				}
				if (data == null) {
					data = new ArrayList<>();
				}
				if (llrpData != null) {
					data.add(0, llrpData);
				}
				// prepend data to pending received data
				int size = 0;
				for (ByteBuffer d : data) {
					size += d.remaining();
				}
				ByteBuffer pendingReceivedData = event.getPendingReceivedData();
				if (pendingReceivedData != null) {
					size += pendingReceivedData.remaining();
				}
				if (size > 0) {
					ByteBuffer pendingData = ByteBuffer.allocate(size);
					for (ByteBuffer d : data) {
						pendingData.put(d);
					}
					if (pendingReceivedData != null) {
						pendingData.put(pendingReceivedData);
					}
					pendingData.flip();
					// create new event with extended data
					event = new LLRPChannelClosedEvent(event.getServerChannel(), channel,
							event.getPendingSendingData(), pendingData, event.getException());
				}
			}
			eventHandler.channelClosed(event);
		}
	}

	/**
	 * Requests the opening of a channel to an host/port. The method
	 * {@link LLRPEventHandler#channelOpened(LLRPChannelOpenedEvent)} of the
	 * event handler is called after the channel has been opened.
	 * 
	 * @param host
	 * @param port
	 * @param eventHandler
	 * @throws IOException
	 * @throws TCPUnknownChannelException
	 */
	public void requestOpeningChannel(String host, int port, LLRPEventHandler eventHandler)
			throws IOException, TCPConnectorStoppedException {
		// create a channel via the TCP client
		tcpRequestOpeningChannel(host, port,
				new TCP2LLRPEventHandler(new LLRPEventHandlerBridge(eventHandler)));
	}

	/**
	 * Requests the sending of a message to a channel.
	 * <p>
	 * The channel must be opened before with
	 * {@link #requestOpeningChannel(String, int, LLRPEventHandler)}.
	 * </p>
	 * <p>
	 * The method {@link LLRPEventHandler#dataSent(LLRPDataSentEvent)} of the
	 * event handler is called after the channel has been sent.
	 * </p>
	 * 
	 * @param channel
	 * @param message
	 * @throws InvalidMessageTypeException
	 * @throws TCPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 * @throws InvalidParameterTypeException
	 */
	public void requestSendingData(SocketChannel channel, Message message)
			throws InvalidMessageTypeException, LLRPUnknownChannelException,
			TCPConnectorStoppedException, InvalidParameterTypeException {
		// serialize message
		ByteBufferSerializer serializer = new ByteBufferSerializer();
		ByteBuffer data = ByteBuffer.allocate((int) serializer.getLength(message));
		serializer.serialize(message, data);
		data.flip();
		// send message
		try {
			tcpRequestSendingData(channel, data);
		} catch (TCPUnknownChannelException e) {
			throw new LLRPUnknownChannelException(e);
		}
		if (log.isLoggable(Level.INFO)) {
			MessageHeader header = message.getMessageHeader();
			log.log(Level.INFO, channel + ": Sending " + header.getMessageType() + " (id="
					+ header.getId() + ", " + header.getMessageLength() + " bytes)");
		}
		if (log.isLoggable(Level.FINE)) {
			log.log(Level.FINE, message.toString());
		}

		tracing(message);

	}

	private void tracing(Message message) {
		if (log.isLoggable(Level.FINER)) {
			JsonSerializer jsonSerializer = new JsonSerializer(Message.class);
			jsonSerializer.setPrettyPrint(true);
			jsonSerializer.addSerializerMixIns(new LLRPJacksonMixIns());
			try {
				log.log(Level.FINER, jsonSerializer.serialize(message));
			} catch (IOException e) {
				log.log(Level.SEVERE, "Cannot serialize the message to JSON", e);
			}
		}

	}

	/**
	 * Dequeues received data for a channel. If the queue is empty the calling
	 * thread changes to {@link State#WAITING} until new data are available or
	 * the specified waiting time elapses.
	 * 
	 * @param channel
	 * @param timeout
	 *            in ms
	 * @return The message
	 * @throws LLRPUnknownChannelException
	 * @throws LLRPTimeoutException
	 *             <ul>
	 *             <li>&lt; 0: wait until data are received (see
	 *             {@link #NO_TIMEOUT})
	 *             <li>0: return existing data immediately (see
	 *             {@link #RETURN_IMMEDIATELY})
	 *             <li>&gt; 0: wait until data are received or the specified
	 *             waiting time elapses (in milliseconds)
	 *             </ul>
	 * @throws InterruptedException
	 * @throws InvalidProtocolVersionException
	 * @throws InvalidMessageTypeException
	 * @throws InvalidParameterTypeException
	 */
	public Message awaitReceivedData(SocketChannel channel, long timeout)
			throws LLRPUnknownChannelException, LLRPTimeoutException, InterruptedException,
			InvalidProtocolVersionException, InvalidMessageTypeException,
			InvalidParameterTypeException {
		long endTime = System.currentTimeMillis() + timeout;
		List<ByteBuffer> newDataList = null;
		while (true) {
			synchronized (dataLock) {
				List<ByteBuffer> dataList = newDataList == null ? receivedData.get(channel)
						: newDataList;
				if (dataList == null) {
					throw new LLRPUnknownChannelException("Unknown channel: " + channel);
				}
				for (ByteBuffer data : dataList.toArray(new ByteBuffer[dataList.size()])) {
					try {
						// move data from the data list to the message creator
						Message message = messageCreator.append(channel, data);
						if (message != null) {
							if (log.isLoggable(Level.INFO)) {
								MessageHeader header = message.getMessageHeader();
								log.log(Level.INFO, channel + ": Received "
										+ header.getMessageType() + " (id=" + header.getId() + ")");
							}
							if (log.isLoggable(Level.FINE)) {
								log.log(Level.FINE, message.toString());
							}
							tracing(message);
							// the remaining data of a new data list must be set
							// to the received data
							receivedData.put(channel, dataList);

							return message;
						}
					} catch (Throwable t) {
						// get existing data for the LLRP message from the
						// message creator and put them back to the list of the
						// received data
						ByteBuffer llrpData = messageCreator.remove(channel);
						dataList.remove(data);
						dataList.add(0, join(llrpData, data));
						receivedData.put(channel, dataList);
						throw t;
					}
					// all data has been moved to the message creator => remove
					// empty byte buffer from data list
					dataList.remove(data);
				}
			}
			long remainingTimeout = timeout;
			// if a time out is set
			if (timeout > 0) {
				remainingTimeout = endTime - System.currentTimeMillis();
				if (remainingTimeout <= 0) {
					throw new LLRPTimeoutException(
							String.format("Time out after %d ms while waiting for received data",
									timeout - remainingTimeout));
				}
			}
			// wait for new data
			try {
				newDataList = tcpAwaitReceivedData(channel, remainingTimeout);
			} catch (TCPTimeoutException e) {
				remainingTimeout = endTime - System.currentTimeMillis();
				throw new LLRPTimeoutException(
						String.format("Time out after %d ms while waiting for received data",
								timeout - remainingTimeout));
			} catch (TCPUnknownChannelException e) {
				throw new LLRPUnknownChannelException(e);
			}
		}
	}

	abstract void tcpRequestOpeningChannel(String host, int port, TCP2LLRPEventHandler eventHandler)
			throws IOException, TCPConnectorStoppedException;

	abstract void tcpRequestSendingData(SocketChannel channel, ByteBuffer data)
			throws TCPUnknownChannelException, TCPConnectorStoppedException;

	/**
	 * @param channel
	 * @param timeout
	 * @return The byte buffer list
	 * @throws TCPUnknownChannelException
	 * @throws InterruptedException
	 * @throws TCPTimeoutException
	 *             <ul>
	 *             <li>&lt; 0: wait until data are received (see
	 *             {@link #NO_TIMEOUT})
	 *             <li>0: return existing data immediately (see
	 *             {@link #RETURN_IMMEDIATELY})
	 *             <li>&gt; 0: wait until data are received or the specified
	 *             waiting time elapses (in milliseconds)
	 *             </ul>
	 */
	abstract List<ByteBuffer> tcpAwaitReceivedData(SocketChannel channel, long timeout)
			throws TCPUnknownChannelException, InterruptedException, TCPTimeoutException;

	/**
	 * Joins byte buffers to a new one.
	 * 
	 * @param data1
	 *            data buffer (ready to read)
	 * @param data2
	 *            data buffer (ready to read)
	 * @return data buffer (ready to read)
	 */
	private ByteBuffer join(ByteBuffer data1, ByteBuffer data2) {
		if (data1 == null) {
			return data2;
		}
		if (data2 == null) {
			return data1;
		} else {
			// join the data
			ByteBuffer retData = null;
			retData = ByteBuffer.allocate(data1.remaining() + data2.remaining());
			retData.put(data1);
			retData.put(data2);
			retData.flip();
			return retData;
		}
	}
}
