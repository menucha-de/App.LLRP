package havis.llrpservice.common.tcp;

import havis.llrpservice.common.tcp.event.TCPChannelClosedEvent;
import havis.llrpservice.common.tcp.event.TCPChannelOpenedEvent;
import havis.llrpservice.common.tcp.event.TCPDataReceivedNotifyEvent;
import havis.llrpservice.common.tcp.event.TCPDataSentEvent;

public interface TCPEventHandler {

	/**
	 * The event is fired after a channel has been opened.
	 * <p>
	 * If the opening of a channel fails then pending data and the occurred
	 * exception are delivered with an {@link TCPChannelClosedEvent}.
	 * </p>
	 * 
	 * @param event
	 */
	public void channelOpened(TCPChannelOpenedEvent event);

	/**
	 * The event is fired after data has been send to a channel. It contains the
	 * data which were sent to the channel.
	 * <p>
	 * If the sending of data fails then the channel is closed. Pending data and
	 * the occurred exception are delivered with an
	 * {@link TCPChannelClosedEvent}.
	 * </p>
	 * 
	 * @param event
	 */
	public void dataSent(TCPDataSentEvent event);

	/**
	 * The notification event is fired after data has been read from a channel.
	 * <p>
	 * If the receiving of data fails then the channel is closed. Pending data
	 * and the occurred exception are delivered with an
	 * {@link TCPChannelClosedEvent}.
	 * </p>
	 * 
	 * @param event
	 */
	public void dataReceived(TCPDataReceivedNotifyEvent event);

	/**
	 * The event is fired after a channel has been closed.
	 * <p>
	 * If a channel must be closed due to an exception the event contains
	 * pending data and the occurred exception.
	 * </p>
	 * 
	 * @param event
	 */
	public void channelClosed(TCPChannelClosedEvent event);
}
