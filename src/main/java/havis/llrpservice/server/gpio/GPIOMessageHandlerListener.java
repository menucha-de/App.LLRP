package havis.llrpservice.server.gpio;

import java.util.List;

public interface GPIOMessageHandlerListener {
	/**
	 * The message handler has been opened.
	 */
	public void opened();

	/**
	 * The message handler has been closed.
	 * 
	 * @param pendingRequests
	 * @param t
	 *            <code>null</code> if the message handler has been closed
	 *            cleanly.
	 */
	public void closed(List<Object> pendingRequests, Throwable t);
}
