package havis.llrpservice.sbc.gpio;

public class GPIOException extends Exception {

	private static final long serialVersionUID = -2192043079390102624L;

	public GPIOException(String message) {
		super(message);
	}

	public GPIOException(String message, Throwable cause) {
		super(message, cause);
	}
}
