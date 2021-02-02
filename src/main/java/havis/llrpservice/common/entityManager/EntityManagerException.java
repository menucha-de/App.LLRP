package havis.llrpservice.common.entityManager;

public class EntityManagerException extends Exception {

	private static final long serialVersionUID = -1746169720461987131L;

	public EntityManagerException(String message) {
		super(message);
	}

	public EntityManagerException(Throwable cause) {
		super(cause);
	}
}
