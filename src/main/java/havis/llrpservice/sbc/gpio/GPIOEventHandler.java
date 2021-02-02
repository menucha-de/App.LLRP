package havis.llrpservice.sbc.gpio;

import havis.llrpservice.sbc.gpio.event.GPIOChannelClosedEvent;
import havis.llrpservice.sbc.gpio.event.GPIOChannelOpenedEvent;
import havis.llrpservice.sbc.gpio.event.GPIODataReceivedNotifyEvent;
import havis.llrpservice.sbc.gpio.event.GPIODataSentEvent;

public interface GPIOEventHandler {

	/**
	 * The event is fired after a channel has been opened.
	 * <p>
	 * If the opening of a channel fails then the pending data which could not
	 * be sent and the occurred exception are delivered with an
	 * {@link GPIOChannelClosedEvent}.
	 * </p>
	 * 
	 * @param event
	 */
	public void channelOpened(GPIOChannelOpenedEvent event);

	/**
	 * The event is fired after a message has been sent to a channel.
	 * <p>
	 * If the sending of data for a message fails then the channel is closed.
	 * Pending data which could not be sent and the occurred exception are
	 * delivered with an {@link GPIOChannelClosedEvent}.
	 * </p>
	 * <p>
	 * If the determination of the message identifier from the sent data fails
	 * the {@link GPIODataSentEvent} event is fired with the sent message data
	 * (pending data) and the occurred exception.
	 * </p>
	 * 
	 * @param event
	 */
	public void dataSent(GPIODataSentEvent event);

	/**
	 * The notification event is fired after data has been read from a channel.
	 * <p>
	 * If the receiving of data fails then the channel is closed. Pending data
	 * and the occurred exception are delivered with an
	 * {@link GPIOChannelClosedEvent}.
	 * </p>
	 * 
	 * @param event
	 */
	public void dataReceived(GPIODataReceivedNotifyEvent event);

	/**
	 * The event is fired after a channel has been closed.
	 * <p>
	 * If a channel must be closed due to an exception the event contains
	 * pending data which could not be sent and the occurred exception.
	 * </p>
	 * 
	 * @param event
	 */
	public void channelClosed(GPIOChannelClosedEvent event);
}
