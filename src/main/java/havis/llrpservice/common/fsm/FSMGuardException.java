package havis.llrpservice.common.fsm;

public class FSMGuardException extends FSMException {

	private static final long serialVersionUID = -4402109487166764127L;

	public FSMGuardException(String message) {
		super(message);
	}

	public FSMGuardException(String message, Throwable cause) {
		super(message, cause);
	}
}
