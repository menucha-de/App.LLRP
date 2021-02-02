package havis.llrpservice.sbc.rfc;

public class RFCException extends Exception {

	private static final long serialVersionUID = -2192043079390102624L;

	public RFCException(String message) {
		super(message);
	}

	public RFCException(String message, Throwable cause) {
		super(message, cause);
	}
}
