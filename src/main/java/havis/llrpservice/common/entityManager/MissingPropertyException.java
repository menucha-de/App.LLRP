package havis.llrpservice.common.entityManager;

public class MissingPropertyException extends EntityManagerException {
	private static final long serialVersionUID = 7442517675824163944L;

	public MissingPropertyException(String message) {
		super(message);
	}

}
