package havis.llrpservice.csc.llrp;

import havis.llrpservice.data.message.Message;
import havis.llrpservice.data.message.MessageHeader;
import havis.llrpservice.data.message.parameter.serializer.InvalidParameterTypeException;
import havis.llrpservice.data.message.serializer.ByteBufferSerializer;
import havis.llrpservice.data.message.serializer.InvalidMessageTypeException;
import havis.llrpservice.data.message.serializer.InvalidProtocolVersionException;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

/**
 * Collects bytes for LLRP messages. If enough data exist for a message then the
 * message data are removed from the message creator and a LLRP message is
 * created and returned.
 */
class LLRPMessageCreator {
	private ByteCollector collector = new ByteCollector();
	private Map<SocketChannel, MessageHeader> messageHeaders = new HashMap<>();

	/**
	 * Appends bytes for a message. If enough data exist for a message then the
	 * message is created and returned.
	 * 
	 * @param key
	 * @param newData
	 *            Data for the message. The buffer must be ready to read. If not
	 *            all data are required then the remaining data are left in the
	 *            buffer (ready to read).
	 * @return The message or <code>null</code> if not enough data exist for
	 *         creating a message.
	 */
	Message append(SocketChannel channel, ByteBuffer data)
			throws InvalidProtocolVersionException,
			InvalidMessageTypeException, InvalidParameterTypeException {
		ByteBufferSerializer serializer = new ByteBufferSerializer();
		while (data.remaining() > 0) {
			if (!collector.containsKey(channel)) {
				// create byte collection for the message header
				collector.addCollection(channel, ByteBuffer
						.allocate(ByteBufferSerializer.MESSAGE_HEADER_LENGTH));
			}
			ByteBuffer collection = collector.append(channel, data);
			if (collection != null) {
				// only empty collections are added => we can use flip to get it
				// ready to read
				collection.flip();
				try {
					MessageHeader messageHeader = messageHeaders.get(channel);
					if (messageHeader == null) {
						// create message header object from byte collection
						messageHeader = serializer
								.deserializeMessageHeader(collection);
						long msgBodyLength = messageHeader.getMessageLength()
								- ByteBufferSerializer.MESSAGE_HEADER_LENGTH;
						if (msgBodyLength > 0) {
							// register the message header for the channel
							messageHeaders.put(channel, messageHeader);
							// create byte collection for the message body
							collector.addCollection(channel,
									ByteBuffer.allocate((int) msgBodyLength));
						} else {
							// create LLRP message object from message header
							return serializer.deserializeMessage(messageHeader,
									collection);
						}
					} else {
						// create LLRP message object from message header and
						// the byte collection
						Message message = serializer.deserializeMessage(
								messageHeader, collection);
						// unregister the message header object
						messageHeaders.remove(channel);
						return message;
					}
				} catch (Throwable t) {
					// put back the full collection to the collector
					// (if new data is appended then the collection is returned
					// immediately and the whole new data are left in the byte
					// buffer)
					collection.position(collection.limit());
					collector.addCollection(channel, collection);
					throw t;
				}
			}
		}
		return null;
	}

	/**
	 * Removes existing data for a channel.
	 * 
	 * @param channel
	 * @return data buffer (ready to read)
	 */
	ByteBuffer remove(SocketChannel channel) {
		// get an existing message header
		MessageHeader messageHeader = messageHeaders.remove(channel);
		// get an open byte collection
		ByteBuffer collection = collector.removeCollection(channel);
		if (collection != null) {
			// only empty collections are added => we can use flip to get it
			// ready to read
			collection.flip();
		}
		if (messageHeader == null) {
			return collection;
		}
		ByteBuffer ret = ByteBuffer
				.allocate(ByteBufferSerializer.MESSAGE_HEADER_LENGTH
						+ (collection == null ? 0 : collection.remaining()));
		ByteBufferSerializer serializer = new ByteBufferSerializer();
		serializer.serialize(messageHeader, ret);
		if (collection != null) {
			ret.put(collection);
		}
		ret.flip();
		return ret;
	}
}
