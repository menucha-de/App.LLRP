package havis.llrpservice.common.tcp;

public class TCPTimeoutException extends Exception {

	private static final long serialVersionUID = 839838772029590261L;

	public TCPTimeoutException(String message) {
		super(message);
	}
}
