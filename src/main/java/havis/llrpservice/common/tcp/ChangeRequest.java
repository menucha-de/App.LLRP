package havis.llrpservice.common.tcp;

import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

class ChangeRequest {
	public enum ChangeType {
		REGISTER, INTERESTED_OP, CLOSE_CHANNEL
	}

	private SelectableChannel channel;
	private ChangeType type;
	private Integer interestedOp;
	private boolean isServerSocketChannel;
	private ByteBuffer sendingData;
	private boolean force;

	/**
	 * Supported combinations:
	 * <ul>
	 * <li>{@link ServerSocketChannel}/{@link SocketChannel},
	 * {@link ChangeType#CLOSE_CHANNEL}, force
	 * <li>{@link ServerSocketChannel}, {@link ChangeType#REGISTER},
	 * {@link SelectionKey#OP_ACCEPT}
	 * <li>{@link SocketChannel}, {@link ChangeType#REGISTER},
	 * {@link SelectionKey#OP_CONNECT}
	 * <li>{@link SocketChannel}, {@link ChangeType#INTERESTED_OP},
	 * {@link SelectionKey#OP_WRITE}, sending data
	 * </ul>
	 * 
	 * @param channel
	 * @param isServerSocketChannel
	 * @param type
	 * @param interestedOp
	 *            one of {@link SelectionKey#OP_ACCEPT},
	 *            {@link SelectionKey#OP_CONNECT}, {@link SelectionKey#OP_WRITE}
	 * @param force
	 * @param sendingData
	 */
	ChangeRequest(SelectableChannel channel, boolean isServerSocketChannel, ChangeType type,
			Integer interestedOp, boolean force, ByteBuffer sendingData) {
		this.channel = channel;
		this.isServerSocketChannel = isServerSocketChannel;
		this.type = type;
		this.interestedOp = interestedOp;
		this.sendingData = sendingData;
		this.force = force;
	}

	SelectableChannel getChannel() {
		return channel;
	}

	ChangeType getType() {
		return type;
	}

	Integer getInterestedOp() {
		return interestedOp;
	}

	boolean isServerSocketChannel() {
		return isServerSocketChannel;
	}

	ByteBuffer getSendingData() {
		return sendingData;
	}

	boolean isForced() {
		return force;
	}
}
