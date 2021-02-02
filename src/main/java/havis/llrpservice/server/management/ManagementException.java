package havis.llrpservice.server.management;

public class ManagementException extends Exception {

	private static final long serialVersionUID = 265828189858916244L;

	public ManagementException(String message) {
		super(message);
	}

	public ManagementException(String message, Throwable cause) {
		super(message, cause);
	}
}
