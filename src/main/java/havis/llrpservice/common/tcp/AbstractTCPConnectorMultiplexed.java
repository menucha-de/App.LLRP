package havis.llrpservice.common.tcp;

import java.io.IOException;
import java.lang.Thread.State;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import havis.llrpservice.common.tcp.ChangeRequest.ChangeType;
import havis.llrpservice.common.tcp.event.TCPChannelClosedEvent;
import havis.llrpservice.common.tcp.event.TCPChannelOpenedEvent;
import havis.llrpservice.common.tcp.event.TCPDataReceivedNotifyEvent;
import havis.llrpservice.common.tcp.event.TCPDataSentEvent;
import havis.llrpservice.common.tcp.event.TCPEvent;

/**
 * The base class for the multiplexed TCP server and client classes
 * {@link TCPServerMultiplexed}, {@link TCPClientMultiplexed}.
 * <p>
 * This class is thread safe.
 * </p>
 */
abstract class AbstractTCPConnectorMultiplexed implements Runnable {

	private static Logger log = Logger.getLogger(AbstractTCPConnectorMultiplexed.class.getName());

	public static final int NO_TIMEOUT = -1;
	public static final int RETURN_IMMEDIATELY = 0;

	private Selector selector;

	private List<TCPEvent> events = new ArrayList<TCPEvent>();
	private Map<SocketChannel, List<ByteBuffer>> sendingQueues = new HashMap<SocketChannel, List<ByteBuffer>>();

	private final Object readBufferSizeLock = new Object();
	private int readBufferSize = 1024;

	private final Object stopLock = new Object();
	private Boolean stop = false;

	private final Lock lock = new ReentrantLock();
	private List<ServerSocketChannel> serverChannels = new ArrayList<ServerSocketChannel>();
	private Map<SocketChannel, ServerSocketChannel> channels = new HashMap<SocketChannel, ServerSocketChannel>();
	private List<ChangeRequest> changeRequests = new ArrayList<ChangeRequest>();
	private Map<SocketChannel, ReceivingQueue> receivingQueues = new HashMap<>();
	List<SelectableChannel> lockedChannels4write = new ArrayList<SelectableChannel>();
	List<SelectableChannel> writtenChannels = new ArrayList<>();

	private class ReceivingQueue {
		List<ByteBuffer> data = new ArrayList<ByteBuffer>();
		Condition notEmpty = lock.newCondition();
	}

	AbstractTCPConnectorMultiplexed() throws IOException {
		// create a new selector
		selector = SelectorProvider.provider().openSelector();
	}

	/**
	 * Requests the sending of data to a channel.
	 * <p>
	 * The channel must be opened before with
	 * {@link TCPClientMultiplexed#requestOpeningChannel(String, int, TCPEventHandler)}
	 * or
	 * {@link TCPServerMultiplexed#requestOpeningChannel(String, int, TCPEventHandler)}
	 * .
	 * </p>
	 * <p>
	 * The method {@link TCPEventHandler#dataSent(TCPDataSentEvent)} of the
	 * event handler is called after the data has been sent.
	 * </p>
	 * <p>
	 * The byte buffer must be ready to read.
	 * </p>
	 * 
	 * @param channel
	 * @param data
	 *            Data to send (ready to read)
	 * @throws TCPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 */
	public void requestSendingData(SocketChannel channel, ByteBuffer data)
			throws TCPUnknownChannelException, TCPConnectorStoppedException {
		// enqueue a sending request for interested write operation (the
		// caller is not the selecting thread)
		enqueueChangeRequest(new ChangeRequest(channel, /* isServerSocketChannel */
				false, ChangeRequest.ChangeType.INTERESTED_OP, SelectionKey.OP_WRITE,
				false /* force */, data));
	}

	/**
	 * Requests the closing of a channel.
	 * <p>
	 * The method {@link TCPEventHandler#channelClosed(TCPChannelClosedEvent)}
	 * of the registered event handler is called after the channel has been
	 * closed. The event handler is registered while opening a channel with
	 * {@link TCPClientMultiplexed#requestOpeningChannel(String, int, TCPEventHandler)}
	 * or
	 * {@link TCPServerMultiplexed#requestOpeningChannel(String, int, TCPEventHandler)}
	 * .
	 * </p>
	 * 
	 * @param channel
	 * @throws TCPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 */
	public void requestClosingChannel(SelectableChannel channel, boolean force)
			throws TCPUnknownChannelException, TCPConnectorStoppedException {
		// enqueue a close request for the channel (the caller is not the
		// selecting thread)
		enqueueChangeRequest(new ChangeRequest(channel, channel instanceof ServerSocketChannel,
				ChangeRequest.ChangeType.CLOSE_CHANNEL, null /* interestedOp */, force,
				null /* sendingData */));
	}

	/**
	 * Requests the closing of the client/server.
	 * <p>
	 * All open channels are closed. For each channel the method
	 * {@link TCPEventHandler#channelClosed(TCPChannelClosedEvent)} of the
	 * registered event handler is called after the channel has been closed. An
	 * event handler is registered while opening a channel with
	 * {@link TCPClientMultiplexed#requestOpeningChannel(String, int, TCPEventHandler)}
	 * or
	 * {@link TCPServerMultiplexed#requestOpeningChannel(String, int, TCPEventHandler)}
	 * .
	 * </p>
	 * <p>
	 * The method {@link #run()} stops when the client/server is down.
	 * </p>
	 */
	public void requestClosing() {
		// set a flag to stop the loop
		synchronized (stopLock) {
			stop = true;
		}
		// wake up the selecting thread if it is blocking
		selector.wakeup();
	}

	/**
	 * Dequeues received data for a channel. If the queue is empty the calling
	 * thread changes to {@link State#WAITING} until new data are available or
	 * the specified waiting time elapses.
	 * 
	 * @param channel
	 * @param timeout
	 *            in ms
	 * @param unit
	 * @return The list of {@link ByteBuffer}
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
	public List<ByteBuffer> awaitReceivedData(SocketChannel channel, long timeout)
			throws TCPUnknownChannelException, InterruptedException, TCPTimeoutException {
		lock.lock();
		try {
			// check for registered channel
			if (!channels.containsKey(channel)) {
				throw new TCPUnknownChannelException("Unknown channel: " + channel);
			}
			ReceivingQueue receivingQueue = getReceivingQueue(channel);
			// change to wait state until new data are available or the
			// specified waiting time elapses
			while (receivingQueue.data.size() == 0 && timeout != 0) {
				if (timeout < 0) {
					receivingQueue.notEmpty.await();
				} else if (!receivingQueue.notEmpty.await(timeout, TimeUnit.MILLISECONDS)) {
					throw new TCPTimeoutException(String.format(
							"Time out after %d ms while waiting for received data", timeout));
				}
				// if the channel has been closed while waiting for data
				if (!channels.containsKey(channel)) {
					throw new TCPUnknownChannelException("Closed channel: " + channel);
				}
			}
			List<ByteBuffer> ret = receivingQueue.data;
			receivingQueue.data = new ArrayList<>();
			return ret;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * The main loop of the client/server.
	 * <p>
	 * This method processes IO operations until {@link #requestClosing()} is
	 * called.
	 * </p>
	 */
	@Override
	public void run() {
		Throwable exception = null;
		while (true) {
			try {
				// process pending change requests
				processChangeRequests();
				// fire pending events
				fireEvents();
				// wait for next IO operation
				new SelectorWrapper(selector).select();
				synchronized (stopLock) {
					if (stop) {
						break;
					}
				}
				// for each key for which events are available
				Set<SelectionKey> keys = selector.selectedKeys();
				for (SelectionKey key : keys.toArray(new SelectionKey[keys.size()])) {
					keys.remove(key);
					if (key.isAcceptable()) {
						accept(key);
					} else if (key.isConnectable()) {
						finishConnect(key);
					} else if (key.isReadable()) {
						read(key);
					} else if (key.isWritable()) {
						if (write(key)) {
							writtenChannels.add(key.channel());
						}
					}
				}
			} catch (Throwable t) {
				exception = t;
				break;
			}
		}
		// get the lock for the change request queue and receiving queue => no
		// external data are accepted
		lock.lock();
		try {
			// close all channels of pending change requests
			// A possible exception is sent with the close events.
			Map<SelectableChannel, Integer> closedChannels = new HashMap<SelectableChannel, Integer>();
			for (ChangeRequest changeRequest : changeRequests
					.toArray(new ChangeRequest[changeRequests.size()])) {
				SelectableChannel channel = changeRequest.getChannel();
				if (!closedChannels.containsKey(channel)) {
					closeChannel(channel, changeRequest.isServerSocketChannel(), exception);
					closedChannels.put(channel, null);
				}
			}
			// close all remaining server channels incl. relating channels
			for (ServerSocketChannel serverChannel : serverChannels
					.toArray(new ServerSocketChannel[serverChannels.size()])) {
				closeChannel(serverChannel, /* isServerSocketChannel */true, exception);
			}
			// close all remaining channels
			for (SocketChannel channel : channels.keySet()
					.toArray(new SocketChannel[channels.size()])) {
				closeChannel(channel, false /* isServerSocketChannel */, exception);
			}
			// avoid the adding of new change requests for this channel after
			// this synchronized block
			// New data cannot be received because the channels have been
			// closed.
			changeRequests = null;
		} finally {
			lock.unlock();
		}
		// fire remaining events
		fireEvents();
		try {
			// close the selector
			new SelectorWrapper(selector).close();
		} catch (Throwable t) {
			if (exception == null) {
				exception = t;
			}
		}
		if (exception != null) {
			log.log(Level.SEVERE, "Main loop stopped with exception: ", exception);
		}
	}

	/**
	 * Enqueues a change request.
	 * 
	 * @param changeRequest
	 * @throws TCPUnknownChannelException
	 * @throws TCPConnectorStoppedException
	 */
	void enqueueChangeRequest(ChangeRequest changeRequest)
			throws TCPUnknownChannelException, TCPConnectorStoppedException {
		lock.lock();
		try {
			if (changeRequests == null) {
				throw new TCPConnectorStoppedException(
						"The request cannot be accepted because the server has been stopped");
			}
			switch (changeRequest.getType()) {
			case REGISTER:
				// "connect" or "accept": we accept new channels if the server
				// is running
				break;
			default:
				SelectableChannel channel = changeRequest.getChannel();
				// the channel must have been registered before
				if (!serverChannels.contains(channel) && !channels.containsKey(channel)) {
					throw new TCPUnknownChannelException("Unknown channel: " + channel);
				}
			}
			// enqueue the change request (the caller is not the selecting
			// thread)
			changeRequests.add(changeRequest);
		} finally {
			lock.unlock();
		}
		// wake up the selecting thread so it can make the required changes
		selector.wakeup();
	}

	/**
	 * See
	 * {@link TCPEventHandler#channelOpened(ServerSocketChannel, SocketChannel)}
	 * 
	 * @param openedChannel
	 * @param openedChannel
	 */
	abstract void channelOpened(TCPChannelOpenedEvent event);

	/**
	 * See {@link TCPEventHandler#dataSent(TCPDataSentEvent)}
	 * 
	 * @param event
	 */
	abstract void dataSent(TCPDataSentEvent event);

	/**
	 * See {@link TCPEventHandler#dataReceived(TCPDataReceivedNotifyEvent)}
	 * 
	 * @param event
	 */
	abstract void dataReceived(TCPDataReceivedNotifyEvent event);

	/**
	 * See {@link TCPEventHandler#channelClosed(TCPChannelClosedEvent)}
	 * 
	 * @param event
	 */
	abstract void channelClosed(TCPChannelClosedEvent event);

	private void fireEvents() {
		for (TCPEvent event : events) {
			if (event instanceof TCPDataSentEvent) {
				dataSent((TCPDataSentEvent) event);
			} else if (event instanceof TCPDataReceivedNotifyEvent) {
				dataReceived((TCPDataReceivedNotifyEvent) event);
			} else if (event instanceof TCPChannelOpenedEvent) {
				channelOpened((TCPChannelOpenedEvent) event);
			} else if (event instanceof TCPChannelClosedEvent) {
				channelClosed((TCPChannelClosedEvent) event);
			}
		}
		events.clear();
	}

	private void processChangeRequests() {
		lock.lock();

		// unlock written channels
		for (SelectableChannel writtenChannel : writtenChannels) {
			// remove channel
			lockedChannels4write.remove(writtenChannel);
			// remove server channel (it does not exist on client side)
			ServerSocketChannel serverChannel = channels.get(writtenChannel);
			if (serverChannel != null) {
				lockedChannels4write.remove(serverChannel);
			}
		}
		writtenChannels.clear();

		try {
			List<SelectableChannel> closingChannels = new ArrayList<>();
			// for each change request
			for (ChangeRequest changeRequest : changeRequests
					.toArray(new ChangeRequest[changeRequests.size()])) {
				// get the socket channel and the server socket channel
				boolean isServerSocketChannel = changeRequest.isServerSocketChannel();
				SelectableChannel changingChannel = changeRequest.getChannel();
				ServerSocketChannel serverChannel = null;
				SocketChannel channel = null;
				if (isServerSocketChannel) {
					serverChannel = (ServerSocketChannel) changingChannel;
				} else {
					channel = (SocketChannel) changingChannel;
					// try to get the server channel
					// (it is not available on client side)
					serverChannel = channels.get(channel);
				}

				// get the root channel
				Integer interestedOp = changeRequest.getInterestedOp();
				boolean isWrite = interestedOp != null && interestedOp == SelectionKey.OP_WRITE;
				boolean isLocked = lockedChannels4write.contains(changingChannel);
				boolean isClosing = closingChannels.contains(changingChannel);
				if (!isClosing && ChangeType.CLOSE_CHANNEL == changeRequest.getType()) {
					if (serverChannel != null) {
						closingChannels.add(serverChannel);
					}
					if (channel != null) {
						closingChannels.add(channel);
					}
					isClosing = true;
				}
				if (!changeRequest.isForced() && isLocked && (!isWrite || isClosing)) {
					// skip the change request (register, close)
					continue;
				} else if (isWrite && !isLocked) {
					// lock channel incl. its server channel due to "write"
					// request (the server channel does not exist on client
					// side)
					if (serverChannel != null) {
						lockedChannels4write.add(serverChannel);
					}
					if (channel != null) {
						lockedChannels4write.add(channel);
					}
				}

				switch (changeRequest.getType()) {
				case INTERESTED_OP:
					SelectionKey key = changingChannel.keyFor(selector);
					if (key != null && key.isValid()) {
						if (interestedOp == SelectionKey.OP_WRITE) {
							// enqueue the data to be sent
							ByteBuffer sendingData = changeRequest.getSendingData();
							if (sendingData != null) {
								enqueueSendingData((SocketChannel) changingChannel, sendingData);
							}
						}
						key.interestOps(interestedOp);
						if (log.isLoggable(Level.FINE)) {
							log.log(Level.FINE,
									changingChannel + ": Switched the interested IO operation to "
											+ getOpsString(interestedOp));
						}
					}
					break;
				case REGISTER:
					try {
						changingChannel.register(selector, interestedOp);
						if (log.isLoggable(Level.FINE)) {
							log.log(Level.FINE, changingChannel + ": Registered for IO operation "
									+ getOpsString(interestedOp));
						}
						if (interestedOp == SelectionKey.OP_ACCEPT) {
							// register the server channel
							serverChannels.add(serverChannel);
							if (log.isLoggable(Level.INFO)) {
								log.log(Level.INFO, changingChannel + ": Opened");
							}
							// enqueue open event for the server channel
							events.add(
									new TCPChannelOpenedEvent(serverChannel, null /* channel */));
						}
					} catch (Throwable t) {
						// close the channel
						closeChannel(changingChannel, isServerSocketChannel, t);
					}
					break;
				case CLOSE_CHANNEL:
					closeChannel(changingChannel, isServerSocketChannel, null /* exception */);
					break;
				}
				changeRequests.remove(changeRequest);
			}
		} finally {
			lock.unlock();
		}
	}

	private void accept(SelectionKey key) {
		ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
		SocketChannel channel = null;
		try {
			// accept the connection and make it non-blocking
			channel = new ServerSocketChannelWrapper(serverChannel).accept();
			channel.configureBlocking(false);
			// register the new channel at the selector starting with read
			// mode
			channel.register(selector, SelectionKey.OP_READ);
		} catch (Throwable t) {
			// close the channel
			if (channel == null) {
				closeChannel(serverChannel, /* isServerSocketChannel */true, t);
			} else {
				closeChannel(channel, /* isServerSocketChannel */false, t);
			}
			return;
		}
		lock.lock();
		try {
			// register the channel
			channels.put(channel, serverChannel);
		} finally {
			lock.unlock();
		}
		if (log.isLoggable(Level.INFO)) {
			log.log(Level.INFO, serverChannel + ": Accepted " + channel);
		}
		// enqueue open event
		events.add(new TCPChannelOpenedEvent(serverChannel, channel));
	}

	private void finishConnect(SelectionKey key) {
		SocketChannel channel = (SocketChannel) key.channel();
		try {
			new SocketChannelWrapper(channel).finishConnect(); // exception if
																// the server is
																// not reachable
		} catch (Throwable t) {
			// close the channel
			closeChannel(channel, /* isServerSocketChannel */false, t);
			return;
		}
		key.interestOps(SelectionKey.OP_READ);
		lock.lock();
		try {
			// register the channel
			channels.put(channel, null);
		} finally {
			lock.unlock();
		}
		if (log.isLoggable(Level.INFO)) {
			log.log(Level.INFO, channel + ": Opened");
		}
		// enqueue open event
		events.add(new TCPChannelOpenedEvent(null, channel));
	}

	private void closeChannel(SelectableChannel channel, boolean isServerSocketChannel,
			Throwable exception) {
		ServerSocketChannel serverChannel = null;
		SocketChannel socketChannel = null;
		List<ByteBuffer> sendingQueue = null;
		ReceivingQueue receivingQueue = null;
		// get the lock for the change request queue and receiving queue => no
		// further external data are accepted
		lock.lock();
		try {
			List<Object> remove = new ArrayList<>();
			// for each change request
			for (ChangeRequest changeRequest : changeRequests) {
				// if a change request for the channel is found
				if (channel.equals(changeRequest.getChannel())) {
					ByteBuffer sendingData = changeRequest.getSendingData();
					// if the request contains pending data
					if (sendingData != null) {
						// enqueue the data
						enqueueSendingData((SocketChannel) channel, sendingData);
					}
					remove.add(changeRequest);
				}
			}
			for (Object obj : remove) {
				changeRequests.remove(obj);
			}
			// unregister the channel => no further external data for the
			// channel are accepted after this synchronized block
			if (isServerSocketChannel) {
				serverChannel = (ServerSocketChannel) channel;
				// for each channel relating to the server channel
				for (SocketChannel c : channels.keySet()
						.toArray(new SocketChannel[channels.size()])) {
					if (channels.get(c) == serverChannel) {
						// close the relating channel
						closeChannel(c, /* isServerSocketChannel */false, exception);
					}
				}
				// unregister the server channel
				serverChannels.remove(channel);
			} else {
				// unregister the channel
				socketChannel = (SocketChannel) channel;
				serverChannel = channels.remove(socketChannel);

				// remove channel and its server channel from locked channels
				// (the server channel does not exist on client side)
				remove.clear();
				for (SelectableChannel c : lockedChannels4write) {
					if (c.equals(channel)) {
						if (serverChannel != null) {
							remove.add(serverChannel);
						}
						remove.add(c);
					}
				}
				for (Object obj : remove) {
					lockedChannels4write.remove(obj);
				}
			}

			// cancel the registration with the selector of the channel
			SelectionKey key = channel.keyFor(selector);
			if (key != null && key.isValid()) {
				key.cancel();
			}
			try {
				// close the channel
				channel.close();
			} catch (Throwable t) {
				if (exception != null) {
					exception = t;
				}
			}
			// get the pending data for the channel
			sendingQueue = sendingQueues.remove(channel);
			receivingQueue = receivingQueues.remove(channel);
			if (log.isLoggable(Level.INFO)) {
				log.log(Level.INFO, channel + ": Closed");
			}
			if (receivingQueue != null) {
				// trigger the throwing of an exception for all threads waiting
				// for data from this channel
				receivingQueue.notEmpty.signalAll();
			}
		} finally {
			lock.unlock();
		}
		// enqueue close event
		ByteBuffer receivingData = null;
		if (receivingQueue != null) {
			receivingData = join(receivingQueue.data);
		}
		events.add(new TCPChannelClosedEvent(serverChannel, socketChannel, join(sendingQueue),
				receivingData, exception));
	}

	private void read(SelectionKey key) {
		SocketChannel channel = (SocketChannel) key.channel();
		ByteBuffer data = ByteBuffer.allocate(getReadBufferSize());
		Throwable exception = null;
		int numRead = -1;
		try {
			numRead = new SocketChannelWrapper(channel).read(data);
			// exception if the remote entity closes the channel
			// while the local entity is reading from the channel
		} catch (Throwable t) {
			exception = t;
		}
		if (numRead == -1) {
			// without an exception: the remote entity has closed the channel
			// cleanly
			closeChannel(channel, /* isServerSocketChannel */false, exception);
			return;
		}
		// make data ready to read
		data.flip();
		ServerSocketChannel serverChannel;
		int queueSize;
		lock.lock();
		try {
			serverChannel = channels.get(channel);
			// enqueue the received data
			ReceivingQueue receivingQueue = getReceivingQueue(channel);
			receivingQueue.data.add(data);
			// wake up a thread waiting for data
			receivingQueue.notEmpty.signal();
			queueSize = receivingQueue.data.size();
		} finally {
			lock.unlock();
		}
		if (numRead > 0 && log.isLoggable(Level.FINE)) {
			log.log(Level.INFO, channel + ": Read " + numRead + " byte" + (numRead != 1 ? "s" : "")
					+ " (queue size: " + queueSize + ")");
		}
		// enqueue data event
		events.add(new TCPDataReceivedNotifyEvent(serverChannel, channel));
	}

	/**
	 * Writes data to a channel.
	 * 
	 * @param key
	 * @return whether all data for the channel are written
	 */
	private boolean write(SelectionKey key) {
		SocketChannel channel = (SocketChannel) key.channel();
		ServerSocketChannel serverChannel;
		lock.lock();
		try {
			serverChannel = channels.get(channel);
		} finally {
			lock.unlock();
		}
		List<ByteBuffer> sendingQueue = sendingQueues.get(channel);
		long numWrite = 0;
		while (!sendingQueue.isEmpty()) {
			ByteBuffer data = sendingQueue.get(0);
			try {
				numWrite += new SocketChannelWrapper(channel).write(data);
				// exception if the remote entity closes the channel
				// while the local entity is writing to the channel
			} catch (Throwable t) {
				// close the channel
				closeChannel(channel, /* isServerSocketChannel */false, t);
				return true;
			}
			// if not all data could be written due to a full socket buffer
			if (data.remaining() > 0) {
				// stop writing here because the socket buffer is full
				// (the remaining data will be written with next 'write'
				// event)
				break;
			}
			// enqueue data event
			events.add(new TCPDataSentEvent(serverChannel, channel, data));
			sendingQueue.remove(0);
		}
		if (numWrite > 0 && log.isLoggable(Level.INFO)) {
			int size = sendingQueue.size();
			log.log(Level.INFO, channel + ": Wrote " + numWrite + " byte"
					+ (numWrite != 1 ? "s" : "") + " (queue size: " + size + ")");
		}
		if (sendingQueue.isEmpty()) {
			// all data has been written => switch back to reading mode
			key.interestOps(SelectionKey.OP_READ);
			if (log.isLoggable(Level.FINE)) {
				log.log(Level.FINE, channel + ": Switched the interested IO operation back to "
						+ getOpsString(SelectionKey.OP_READ));
			}
			return true;
		}
		return false;
	}

	/**
	 * Gets the receiving queue for a channel. The call of this method must be
	 * protected with {@link #lock}.
	 * 
	 * @param channel
	 * @return The receiving queue
	 */
	private ReceivingQueue getReceivingQueue(SocketChannel channel) {
		ReceivingQueue receivingQueue = receivingQueues.get(channel);
		if (receivingQueue == null) {
			receivingQueue = new ReceivingQueue();
			receivingQueues.put(channel, receivingQueue);
		}
		return receivingQueue;
	}

	/**
	 * Enqueues data for sending.
	 * 
	 * @param channel
	 * @param data
	 * @return The queue size
	 */
	private void enqueueSendingData(SocketChannel channel, ByteBuffer data) {
		List<ByteBuffer> sendingQueue = sendingQueues.get(channel);
		if (sendingQueue == null) {
			sendingQueue = new ArrayList<ByteBuffer>();
			sendingQueues.put(channel, sendingQueue);
		}
		sendingQueue.add(data);
	}

	/**
	 * Converts an operation {@link SelectionKey#OP_ACCEPT},
	 * {@link SelectionKey#OP_CONNECT}, {@link SelectionKey#OP_READ} or
	 * {@link SelectionKey#OP_WRITE} to a readable string.
	 * 
	 * @param operation
	 * @return The operation string
	 */
	private String getOpsString(int operation) {
		if ((operation & SelectionKey.OP_ACCEPT) != 0) {
			return "accept";
		}
		if ((operation & SelectionKey.OP_CONNECT) != 0) {
			return "connect";
		}
		if ((operation & SelectionKey.OP_READ) != 0) {
			return "read";
		}
		if ((operation & SelectionKey.OP_WRITE) != 0) {
			return "write";
		}
		return null;
	}

	/**
	 * Joins a list of byte buffers to one new byte buffer.
	 * 
	 * @param data
	 * @return The byte buffer
	 */
	private ByteBuffer join(List<ByteBuffer> data) {
		if (data == null) {
			return null;
		}
		int size = 0;
		for (ByteBuffer buf : data) {
			size += buf.remaining();
		}
		if (size == 0) {
			return null;
		}
		ByteBuffer ret = ByteBuffer.allocate(size);
		for (ByteBuffer buf : data) {
			ret.put(buf);
		}
		ret.flip();
		return ret;
	}

	public int getReadBufferSize() {
		synchronized (readBufferSizeLock) {
			return readBufferSize;
		}
	}

	public void setReadBufferSize(int readBufferSize) {
		synchronized (readBufferSizeLock) {
			this.readBufferSize = readBufferSize;
		}
	}
}
