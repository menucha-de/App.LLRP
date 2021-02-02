package havis.llrpservice.server.service.fsm;

public class InvalidMacAddressException extends Exception {

	private static final long serialVersionUID = -2228820060387490542L;

	public InvalidMacAddressException(String msg) {
		super(msg);
	}
}
