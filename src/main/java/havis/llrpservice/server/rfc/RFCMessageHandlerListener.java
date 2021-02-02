package havis.llrpservice.server.rfc;

import java.util.List;

public interface RFCMessageHandlerListener {
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

	/**
	 * An AccessSpec has been removed eg. because the max. operation count has
	 * reached.
	 * 
	 * @param accessSpecId
	 */
	public void removedAccessSpec(long accessSpecId);
}
