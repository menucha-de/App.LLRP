package havis.llrpservice.sbc.rfc;

import havis.llrpservice.sbc.rfc.event.RFCChannelClosedEvent;
import havis.llrpservice.sbc.rfc.event.RFCChannelOpenedEvent;
import havis.llrpservice.sbc.rfc.event.RFCDataReceivedNotifyEvent;
import havis.llrpservice.sbc.rfc.event.RFCDataSentEvent;

public interface RFCEventHandler {

	/**
	 * The event is fired after a channel has been opened.
	 * <p>
	 * If the opening of a channel fails then the pending data which could not
	 * be sent and the occurred exception are delivered with an
	 * {@link RFCChannelClosedEvent}.
	 * </p>
	 * 
	 * @param event
	 */
	public void channelOpened(RFCChannelOpenedEvent event);

	/**
	 * The event is fired after a message has been sent to a channel.
	 * <p>
	 * If the sending of data for a message fails then the channel is closed.
	 * Pending data which could not be sent and the occurred exception are
	 * delivered with an {@link RFCChannelClosedEvent}.
	 * </p>
	 * <p>
	 * If the determination of the message identifier from the sent data fails
	 * the {@link RFCDataSentEvent} event is fired with the sent message data
	 * (pending data) and the occurred exception.
	 * </p>
	 * 
	 * @param event
	 */
	public void dataSent(RFCDataSentEvent event);

	/**
	 * The notification event is fired after data has been read from a channel.
	 * <p>
	 * If the receiving of data fails then the channel is closed. Pending data
	 * and the occurred exception are delivered with an
	 * {@link RFCChannelClosedEvent}.
	 * </p>
	 * 
	 * @param event
	 */
	public void dataReceived(RFCDataReceivedNotifyEvent event);

	/**
	 * The event is fired after a channel has been closed.
	 * <p>
	 * If a channel must be closed due to an exception the event contains
	 * pending data which could not be sent and the occurred exception.
	 * </p>
	 * 
	 * @param event
	 */
	public void channelClosed(RFCChannelClosedEvent event);
}
