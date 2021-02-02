package havis.llrpservice.server.llrp;

import havis.llrpservice.csc.llrp.event.LLRPChannelClosedEvent;
import havis.llrpservice.csc.llrp.event.LLRPChannelOpenedEvent;
import havis.llrpservice.csc.llrp.event.LLRPDataSentEvent;

public interface LLRPMessageHandlerListener {
	/**
	 * The message handler has been opened.
	 */
	public void opened();

	/**
	 * The message handler has been closed.
	 * 
	 * @param t
	 *            <code>null</code> if the message handler has been closed
	 *            cleanly.
	 */
	public void closed(Throwable t);

	/**
	 * A client connection has been established.
	 * 
	 * @param event
	 */
	public void opened(LLRPChannelOpenedEvent event);

	/**
	 * A client connection has been closed.
	 * 
	 * @param event
	 */
	public void closed(LLRPChannelClosedEvent event);

	/**
	 * Data has been sent to the client.
	 * 
	 * @param event
	 */
	public void dataSent(LLRPDataSentEvent event);
}
