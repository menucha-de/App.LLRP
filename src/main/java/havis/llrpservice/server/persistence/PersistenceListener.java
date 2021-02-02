package havis.llrpservice.server.persistence;

import java.util.List;

public interface PersistenceListener {
	public void added(ObservablePersistence src, List<String> entityIds);
	public void removed(ObservablePersistence src, List<String> entityIds);
	public void updated (ObservablePersistence src, List<String> entityIds);
}
