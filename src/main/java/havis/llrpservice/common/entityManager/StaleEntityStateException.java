package havis.llrpservice.common.entityManager;

public class StaleEntityStateException extends EntityManagerException {

	private static final long serialVersionUID = 7359444771214887301L;

	public StaleEntityStateException(String message) {
		super(message);
	}

}
