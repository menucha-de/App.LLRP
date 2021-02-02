package havis.llrpservice.server.persistence;

import havis.llrpservice.common.serializer.XMLSerializer;
import havis.llrpservice.xml.configuration.CleanUpType;
import havis.llrpservice.xml.configuration.EntityType;
import havis.llrpservice.xml.configuration.GroupIdType;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;
import havis.llrpservice.xml.configuration.OutputType;
import havis.llrpservice.xml.configuration.PersistTimesType;

import java.io.IOException;

import org.jibx.runtime.JiBXException;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.xml.sax.SAXException;

public class PersistenceConfigAnalyserTest {

	private final static String serverConfigFile = "/havis/llrpservice/server/persistence/persistenceConfigAnalyser/LLRPServerConfiguration.xml";
	private final static String instanceConfigFile = "/havis/llrpservice/server/persistence/persistenceConfigAnalyser/LLRPServerInstanceConfiguration.xml";

	@Test
	public void getCleanUp() throws IOException, JiBXException, SAXException {
		LLRPServerConfigurationType config = readConfig(serverConfigFile,
				LLRPServerConfigurationType.class);
		PersistenceConfigAnalyser ca = new PersistenceConfigAnalyser();
		// try to get a config part without a config
		Assert.assertNull(ca.getCleanUp(LLRPServerConfigurationType.class
				.getName()));
		// set a server config
		ca.setServerConfig(config, "");
		// get config part without an entity configuration
		CleanUpType cu = ca.getCleanUp("notConfiguredClassName");
		Assert.assertEquals(cu.getMaxCreationDateInterval(), new Integer(1));
		// get config part of an entity configuration
		cu = ca.getCleanUp(LLRPServerConfigurationType.class.getName());
		Assert.assertEquals(cu.getMaxCreationDateInterval(), new Integer(23));

		// remove config part from server config but not the defaults
		config.setPersistence(null);
		ca.setServerConfig(config, "");
		// get config part of default entity configuration
		cu = ca.getCleanUp(LLRPServerConfigurationType.class.getName());
		Assert.assertEquals(cu.getMaxCreationDateInterval(), new Integer(2));

		// set a server instance config
		LLRPServerInstanceConfigurationType instanceConfig = readConfig(
				instanceConfigFile, LLRPServerInstanceConfigurationType.class);
		ca.setServerInstanceConfig(instanceConfig, "");
		// get config part without an entity configuration
		cu = ca.getCleanUp("notConfiguredClassName");
		Assert.assertEquals(cu.getMaxCreationDateInterval(), new Integer(3));
		// get config part of an entity configuration
		cu = ca.getCleanUp(LLRPServerInstanceConfigurationType.class.getName());
		Assert.assertEquals(cu.getMaxCreationDateInterval(), new Integer(4));

		// remove all cleanUp config parts (clean up disabled) and try to get a
		// config part
		config.getDefaults().getPersistence().setCleanUp(null);
		for (EntityType entity : config.getDefaults().getPersistence()
				.getEntities().getEntityList()) {
			entity.setCleanUp(null);
		}
		instanceConfig.getPersistence().setCleanUp(null);
		for (EntityType entity : instanceConfig.getPersistence().getEntities()
				.getEntityList()) {
			entity.setCleanUp(null);
		}
		Assert.assertNull(ca
				.getCleanUp(LLRPServerInstanceConfigurationType.class.getName()));
	}

	@Test
	public void getGroupId() throws IOException, JiBXException, SAXException {
		LLRPServerConfigurationType config = readConfig(serverConfigFile,
				LLRPServerConfigurationType.class);
		PersistenceConfigAnalyser ca = new PersistenceConfigAnalyser();
		// try to get a config part without a config
		Assert.assertNull(ca.getGroupId(LLRPServerConfigurationType.class
				.getName()));
		// set a server config
		ca.setServerConfig(config, "");
		// get config part without an entity configuration
		GroupIdType gid = ca.getGroupId("notConfiguredClassName");
		Assert.assertEquals(gid.getBaseFormat(), "yyyy");
		// get config part of an entity configuration
		gid = ca.getGroupId(LLRPServerConfigurationType.class.getName());
		Assert.assertEquals(gid.getBaseFormat(), "MM_overwritten");

		// remove config part from server config but not the defaults
		config.setPersistence(null);
		ca.setServerConfig(config, "");
		// get config part of default entity configuration
		gid = ca.getGroupId(LLRPServerConfigurationType.class.getName());
		Assert.assertEquals(gid.getBaseFormat(), "MM");

		// set a server instance config
		LLRPServerInstanceConfigurationType instanceConfig = readConfig(
				instanceConfigFile, LLRPServerInstanceConfigurationType.class);
		ca.setServerInstanceConfig(instanceConfig, "");
		// get config part without an entity configuration
		gid = ca.getGroupId("notConfiguredClassName");
		Assert.assertEquals(gid.getBaseFormat(), "dd");
		// get config part of an entity configuration
		gid = ca.getGroupId(LLRPServerInstanceConfigurationType.class.getName());
		Assert.assertEquals(gid.getBaseFormat(), "HH");
	}

	@Test
	public void getOutput() throws IOException, JiBXException, SAXException {
		LLRPServerConfigurationType config = readConfig(serverConfigFile,
				LLRPServerConfigurationType.class);
		PersistenceConfigAnalyser ca = new PersistenceConfigAnalyser();
		// try to get a config part without a config
		Assert.assertNull(ca.getOutput(LLRPServerConfigurationType.class
				.getName()));
		// set a server config
		ca.setServerConfig(config, "");
		// try to get a config part without an entity configuration
		Assert.assertNull(ca.getOutput("notConfiguredClassName"));
		// get config part of an entity configuration
		OutputType out = ca.getOutput(LLRPServerConfigurationType.class
				.getName());
		Assert.assertTrue(out.getFile().getFileProperties().getBaseDir()
				.endsWith("serverConfig_overwritten"));

		// remove config part from server config but not the defaults
		config.setPersistence(null);
		ca.setServerConfig(config, "");
		// get config part of default entity configuration
		out = ca.getOutput(LLRPServerConfigurationType.class.getName());
		Assert.assertTrue(out.getFile().getFileProperties().getBaseDir()
				.endsWith("serverConfig"));

		// set a server instance config
		LLRPServerInstanceConfigurationType instanceConfig = readConfig(
				instanceConfigFile, LLRPServerInstanceConfigurationType.class);
		ca.setServerInstanceConfig(instanceConfig, "");
		// try to get a config part without an entity configuration
		Assert.assertNull(ca.getOutput("notConfiguredClassName"));
		// get config part of an entity configuration
		out = ca.getOutput(LLRPServerInstanceConfigurationType.class.getName());
		Assert.assertTrue(out.getFile().getFileProperties().getBaseDir()
				.endsWith("serverInstanceConfig"));
	}

	@Test
	public void getTimes() throws IOException, JiBXException, SAXException {
		LLRPServerConfigurationType config = readConfig(serverConfigFile,
				LLRPServerConfigurationType.class);
		PersistenceConfigAnalyser ca = new PersistenceConfigAnalyser();
		// try to get a config part without a config
		Assert.assertNull(ca.getTimes(LLRPServerConfigurationType.class
				.getName()));
		// set a server config
		ca.setServerConfig(config, "");
		// get config part without an entity configuration
		PersistTimesType ti = ca.getTimes("notConfiguredClassName");
		Assert.assertFalse(ti.isManual());
		Assert.assertFalse(ti.isAfterChanges());
		// get config part of an entity configuration
		ti = ca.getTimes(LLRPServerConfigurationType.class.getName());
		Assert.assertFalse(ti.isManual());
		Assert.assertTrue(ti.isAfterChanges());

		// remove config part from server config but not the defaults
		config.setPersistence(null);
		ca.setServerConfig(config, "");
		// get config part of default entity configuration
		ti = ca.getTimes(LLRPServerConfigurationType.class.getName());
		Assert.assertTrue(ti.isManual());
		Assert.assertFalse(ti.isAfterChanges());

		// set a server instance config
		LLRPServerInstanceConfigurationType instanceConfig = readConfig(
				instanceConfigFile, LLRPServerInstanceConfigurationType.class);
		ca.setServerInstanceConfig(instanceConfig, "");
		// get config part without an entity configuration
		ti = ca.getTimes("notConfiguredClassName");
		Assert.assertFalse(ti.isManual());
		Assert.assertTrue(ti.isAfterChanges());
		// get config part of an entity configuration
		ti = ca.getTimes(LLRPServerInstanceConfigurationType.class.getName());
		Assert.assertTrue(ti.isManual());
		Assert.assertTrue(ti.isAfterChanges());
	}

	private <T> T readConfig(String fileName, Class<T> clazz)
			throws IOException, JiBXException, SAXException {
		String configXML = _FileHelperTest.readFile(getClass()
				.getResourceAsStream(fileName));
		XMLSerializer<T> serializer = new XMLSerializer<>(clazz);
		return serializer.deserialize(configXML);
	}
}
