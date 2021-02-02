package havis.llrpservice.server.persistence;

public class PersistenceException extends Exception {

	private static final long serialVersionUID = -6300973502438558567L;

	public PersistenceException(String message) {
		super(message);
	}

	public PersistenceException(Throwable t) {
		super(t);
	}

}
