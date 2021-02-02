package havis.llrpservice.csc.llrp;

public class LLRPUnknownChannelException extends Exception {

	private static final long serialVersionUID = 9208691144032573627L;

	public LLRPUnknownChannelException(String message) {
		super(message);
	}

	public LLRPUnknownChannelException(Throwable cause) {
		super(cause);
	}
}
