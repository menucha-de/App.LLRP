package havis.llrpservice.server.service;

import java.util.regex.Pattern;

import havis.llrpservice.xml.properties.LLRPServerInstancePropertiesType;

/**
 * The PropertiesValidator validates LLRP properties.
 */
public class PropertiesValidator {

	private Pattern instanceIdPattern = Pattern.compile("^[a-zA-Z0-9_]+$");

	/**
	 * Validates server instance properties.
	 * 
	 * @param props
	 *            content of a properties file
	 * @param filePath
	 *            the path to the properties file
	 * @throws PropertiesException
	 */
	public void validate(LLRPServerInstancePropertiesType props, String filePath)
			throws PropertiesException {
		if (props == null) {
			return;
		}
		if (!instanceIdPattern.matcher(props.getInstanceId()).matches()) {
			throw new PropertiesException("Invalid instance identifier '"
					+ props.getInstanceId() + "' at " + filePath
					+ ":/LLRPServerInstanceProperties/instanceId");
		}
	}
}
