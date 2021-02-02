package havis.llrpservice.server.service;

import static org.testng.Assert.*;

import org.testng.annotations.Test;

import havis.llrpservice.xml.properties.DefaultsGroup;
import havis.llrpservice.xml.properties.DefaultsType;
import havis.llrpservice.xml.properties.LLRPServerInstancePropertiesType;
import havis.llrpservice.xml.properties.LLRPServerPropertiesType;

public class PropertiesAnalyserTest {

	@Test
	public void getInstancesProperties() throws Exception {
		// get instance properties from server configuration
		LLRPServerPropertiesType serverProps = new LLRPServerPropertiesType();
		DefaultsType instances = new DefaultsType();
		DefaultsGroup defaultsGroup = new DefaultsGroup();
		defaultsGroup.setMaxStartupRetries(5);
		instances.setDefaultsGroup(defaultsGroup);
		serverProps.setDefaults(instances);

		PropertiesAnalyser pa = new PropertiesAnalyser(serverProps);
		DefaultsGroup paInstanceGroup = pa.getInstancesProperties();
		// a copy has been returned
		assertNotEquals(paInstanceGroup, serverProps.getDefaults().getDefaultsGroup());
		assertEquals(paInstanceGroup.getMaxStartupRetries(), 5);

		// add instance properties
		LLRPServerInstancePropertiesType instanceProps = new LLRPServerInstancePropertiesType();
		defaultsGroup = new DefaultsGroup();
		defaultsGroup.setMaxStartupRetries(6);
		instanceProps.setDefaultsGroup(defaultsGroup);

		pa.setServerInstanceProperties(instanceProps);
		paInstanceGroup = pa.getInstancesProperties();
		// the instance properties are returned directly
		assertEquals(paInstanceGroup, defaultsGroup);
		assertEquals(paInstanceGroup.getMaxStartupRetries(), 6);
	}
}
