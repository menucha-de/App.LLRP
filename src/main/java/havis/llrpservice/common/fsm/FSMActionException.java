package havis.llrpservice.common.fsm;

public class FSMActionException extends FSMException {

	private static final long serialVersionUID = 8864411350950829399L;

	public FSMActionException(String message) {
		super(message);
	}

	public FSMActionException(String message, Throwable cause) {
		super(message, cause);
	}
}
