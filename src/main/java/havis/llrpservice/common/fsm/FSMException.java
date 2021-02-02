package havis.llrpservice.common.fsm;

public class FSMException extends Exception {

	private static final long serialVersionUID = 7572183868915883925L;

	public FSMException(String message) {
		super(message);
	}

	public FSMException(String message, Throwable cause) {
		super(message, cause);
	}
}
