package havis.llrpservice.common.ids;

public class IdGenerator {
	private static long longId = 0;

	public synchronized static long getNextLongId() {
		return ++longId;
	}

	public synchronized static void resetLongId() {
		longId = 0;
	}
}
