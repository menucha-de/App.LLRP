package havis.llrpservice.server.service;

import havis.llrpservice.xml.properties.DefaultsGroup;
import havis.llrpservice.xml.properties.LLRPServerInstancePropertiesType;
import havis.llrpservice.xml.properties.LLRPServerPropertiesType;

import java.io.IOException;

import org.jibx.runtime.JiBXException;
import org.xml.sax.SAXException;

import com.rits.cloning.Cloner;

/**
 * Analysis server instance properties.
 */
class PropertiesAnalyser {

	private LLRPServerInstancePropertiesType serverInstanceProperties;
	private LLRPServerPropertiesType serverProperties;

	public PropertiesAnalyser(LLRPServerPropertiesType serverProperties) {
		this.serverProperties = serverProperties;
	}

	public void setServerInstanceProperties(
			LLRPServerInstancePropertiesType serverInstanceProperties) {
		this.serverInstanceProperties = serverInstanceProperties;
	}

	/**
	 * Gets the instance properties. If the instance properties from the server
	 * properties are used then a copy of the data structure is returned.
	 * 
	 * @return
	 * @throws IOException
	 * @throws JiBXException
	 * @throws SAXException
	 */
	public DefaultsGroup getInstancesProperties() throws IOException,
			JiBXException, SAXException {
		DefaultsGroup instancesProperties = null;
		if (serverInstanceProperties != null) {
			instancesProperties = serverInstanceProperties.getDefaultsGroup();
		}
		if (instancesProperties == null) {
			instancesProperties = new Cloner().deepClone(serverProperties
					.getDefaults().getDefaultsGroup());
		}
		return instancesProperties;
	}
}
