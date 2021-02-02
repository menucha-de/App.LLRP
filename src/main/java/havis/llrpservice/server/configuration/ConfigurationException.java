package havis.llrpservice.server.configuration;

public class ConfigurationException extends Exception {
	private static final long serialVersionUID = 7535023035329585748L;

	public ConfigurationException(Throwable t) {
		super(t);
	}

	public ConfigurationException(String message) {
		super(message);
	}
}
