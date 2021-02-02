package havis.llrpservice.common.entityManager;

public class UnknownEntityException extends EntityManagerException {
	private static final long serialVersionUID = 7442517675824163944L;

	public UnknownEntityException(String message) {
		super(message);
	}

}
