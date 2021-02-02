package havis.llrpservice.common.entityManager;

public class WrongMetaDataException extends EntityManagerException {
	private static final long serialVersionUID = -7179782198647046411L;

	public WrongMetaDataException(String message) {
		super(message);
	}
}
