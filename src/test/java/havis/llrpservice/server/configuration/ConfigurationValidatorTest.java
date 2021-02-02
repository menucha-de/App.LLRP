package havis.llrpservice.server.configuration;

import java.nio.file.Path;

import havis.llrpservice.common.io.PathHandler;
import havis.llrpservice.common.io.XMLFile;
import havis.llrpservice.xml.configuration.EntityType;
import havis.llrpservice.xml.configuration.GroupIdType;
import havis.llrpservice.xml.configuration.InstancePersistenceType;
import havis.llrpservice.xml.configuration.JSONType;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;
import havis.llrpservice.xml.configuration.PersistenceType;
import havis.llrpservice.xml.configuration.ReflectionType;
import havis.llrpservice.xml.configuration.ServerPersistenceType;

import org.testng.Assert;
import org.testng.annotations.Test;

public class ConfigurationValidatorTest {

	private static final Path BASE_PATH = new PathHandler()
			.toAbsolutePath("havis/llrpservice/server/configuration/configurationValidator");
	private static final Path CONF_PATH = BASE_PATH
			.resolve("LLRPServerConfiguration.xml");
	private static final Path INSTANCE_CONF_PATH = BASE_PATH
			.resolve("LLRPServerInstanceConfiguration.xml");

	@Test
	public void validateServerConfiguration() throws Exception {
		// load server configuration
		LLRPServerConfigurationType conf = new XMLFile<>(
				LLRPServerConfigurationType.class, CONF_PATH, null /* latestPath */)
				.getContent();
		ConfigurationValidator cv = new ConfigurationValidator();
		// validate without a config
		cv.validate((LLRPServerConfigurationType) null /* config */, null /* filePath */);
		// check the valid config
		cv.validate(conf, CONF_PATH.toString());

		String validValue = conf.getSchemaVersion();
		conf.setSchemaVersion(null);
		cv.validate(conf, CONF_PATH.toString());
		String[] invalidSchemaVersions = { "", "1.1.1", "x" };
		for (String invalidSchemaVersion : invalidSchemaVersions) {
			conf.setSchemaVersion(invalidSchemaVersion);
			validate(conf, CONF_PATH.toString(),
					"LLRPServerConfiguration.xml:/LLRPServerConfiguration/schemaVersion");
		}
		conf.setSchemaVersion(validValue);

		ReflectionType reflection = conf.getDefaults().getInterfaces().getRFC()
				.getRfcPortProperties().getReflection();
		validValue = reflection.getControllerClassName();
		String[] invalidControllerClassNames = { "", "huhu",
				"java.lang.Integer" /* does not implement RFController interface */};
		for (String invalidControllerClassName : invalidControllerClassNames) {
			reflection.setControllerClassName(invalidControllerClassName);
			validate(
					conf,
					CONF_PATH.toString(),
					"LLRPServerConfiguration.xml:/LLRPServerConfiguration/defaults/interfaces/RFC/reflection/controllerClassName");
		}
		reflection.setControllerClassName(validValue);

		PersistenceType persistence = conf.getDefaults().getPersistence();
		GroupIdType groupId = persistence.getGroupId();
		validValue = groupId.getBaseFormat();
		String[] invalidBaseFormats = { "", "x" };
		for (String invalidBaseFormat : invalidBaseFormats) {
			groupId.setBaseFormat(invalidBaseFormat);
			validate(
					conf,
					CONF_PATH.toString(),
					"LLRPServerConfiguration.xml:/LLRPServerConfiguration/defaults/persistence/groupId/baseFormat");
		}
		groupId.setBaseFormat(validValue);

		EntityType entity = persistence.getEntities().getEntityList().get(0);
		validValue = entity.getClassName();
		String[] invalidClassNames = { "", "x" };
		for (String invalidClassName : invalidClassNames) {
			entity.setClassName(invalidClassName);
			validate(
					conf,
					CONF_PATH.toString(),
					"LLRPServerConfiguration.xml:/LLRPServerConfiguration/defaults/persistence/entities/entity/className");
		}
		entity.setClassName(validValue);

		JSONType json = entity.getOutput().getFile().getFileProperties()
				.getType().getJSON();
		validValue = json.getMixInClassName();
		json.setMixInClassName("huhu");
		validate(
				conf,
				CONF_PATH.toString(),
				"LLRPServerConfiguration.xml:/LLRPServerConfiguration/defaults/persistence/entities/entity/output/file/type/JSON/mixInClassName");
		json.setMixInClassName(validValue);

		groupId = entity.getGroupId();
		validValue = groupId.getBaseFormat();
		groupId.setBaseFormat("x");
		validate(
				conf,
				CONF_PATH.toString(),
				"LLRPServerConfiguration.xml:/LLRPServerConfiguration/defaults/persistence/entities/entity/groupId/baseFormat");
		groupId.setBaseFormat(validValue);

		ServerPersistenceType serverPersistence = conf.getPersistence();
		groupId = serverPersistence.getGroupId();
		validValue = groupId.getBaseFormat();
		groupId.setBaseFormat("x");
		validate(
				conf,
				CONF_PATH.toString(),
				"LLRPServerConfiguration.xml:/LLRPServerConfiguration/persistence/groupId/baseFormat");
		groupId.setBaseFormat(validValue);

		entity = serverPersistence.getEntities().getEntityList().get(0);
		validValue = entity.getClassName();
		String[] invalidClassNames2 = { "", "x" };
		for (String invalidClassName : invalidClassNames2) {
			entity.setClassName(invalidClassName);
			validate(
					conf,
					CONF_PATH.toString(),
					"LLRPServerConfiguration.xml:/LLRPServerConfiguration/persistence/entities/entity/className");
		}
		entity.setClassName(validValue);

		json = entity.getOutput().getFile().getFileProperties().getType()
				.getJSON();
		validValue = json.getMixInClassName();
		json.setMixInClassName("huhu");
		validate(
				conf,
				CONF_PATH.toString(),
				"LLRPServerConfiguration.xml:/LLRPServerConfiguration/persistence/entities/entity/output/file/type/JSON/mixInClassName");
		json.setMixInClassName(validValue);

		groupId = entity.getGroupId();
		validValue = groupId.getBaseFormat();
		groupId.setBaseFormat("x");
		validate(
				conf,
				CONF_PATH.toString(),
				"LLRPServerConfiguration.xml:/LLRPServerConfiguration/persistence/entities/entity/groupId/baseFormat");
		groupId.setBaseFormat(validValue);

		// remove optional JSON MixIn class name
		entity.getOutput().getFile().getFileProperties().getType().getJSON()
				.setMixInClassName(null);
		cv.validate(conf, CONF_PATH.toString());

		// remove optional entities
		conf.getPersistence().setEntities(null);
		cv.validate(conf, CONF_PATH.toString());
	}

	@Test
	public void validateServerInstanceConfiguration() throws Exception {
		// load server instance configuration
		LLRPServerInstanceConfigurationType conf = new XMLFile<>(
				LLRPServerInstanceConfigurationType.class, INSTANCE_CONF_PATH,
				null /* latestPath */).getContent();
		ConfigurationValidator cv = new ConfigurationValidator();
		cv.validate((LLRPServerInstanceConfigurationType) null /* config */,
				null /* filePath */);
		// validate the valid config
		cv.validate(conf, INSTANCE_CONF_PATH.toString());

		String validValue = conf.getSchemaVersion();
		conf.setSchemaVersion(null);
		cv.validate(conf, INSTANCE_CONF_PATH.toString());
		String[] invalidSchemaVersions = { "", "1.1.1", "x" };
		for (String invalidSchemaVersion : invalidSchemaVersions) {
			conf.setSchemaVersion(invalidSchemaVersion);
			validate(
					conf,
					INSTANCE_CONF_PATH.toString(),
					"LLRPServerInstanceConfiguration.xml:/LLRPServerInstanceConfiguration/schemaVersion");
		}
		conf.setSchemaVersion(validValue);

		validValue = conf.getInstanceId();
		String[] invalidInstanceIds = { "", "defaults+" };
		for (String invalidInstanceId : invalidInstanceIds) {
			conf.setInstanceId(invalidInstanceId);
			validate(
					conf,
					INSTANCE_CONF_PATH.toString(),
					"LLRPServerInstanceConfiguration.xml:/LLRPServerInstanceConfiguration/instanceId");
		}
		conf.setInstanceId(validValue);

		ReflectionType reflection = conf.getInterfaces().getRFC()
				.getRfcPortProperties().getReflection();
		validValue = reflection.getControllerClassName();
		String[] invalidControllerClassNames = { "", "huhu",
				"java.lang.Integer" /* does not implement RFController interface */};
		for (String invalidControllerClassName : invalidControllerClassNames) {
			reflection.setControllerClassName(invalidControllerClassName);
			validate(
					conf,
					INSTANCE_CONF_PATH.toString(),
					"LLRPServerInstanceConfiguration.xml:/LLRPServerInstanceConfiguration/interfaces/RFC/reflection/controllerClassName");
		}
		reflection.setControllerClassName(validValue);

		InstancePersistenceType instancePersistence = conf.getPersistence();
		GroupIdType groupId = instancePersistence.getGroupId();
		validValue = groupId.getBaseFormat();
		String[] invalidBaseFormats = { "", "x" };
		for (String invalidBaseFormat : invalidBaseFormats) {
			groupId.setBaseFormat(invalidBaseFormat);
			validate(
					conf,
					INSTANCE_CONF_PATH.toString(),
					"LLRPServerInstanceConfiguration.xml:/LLRPServerInstanceConfiguration/persistence/groupId/baseFormat");
		}
		groupId.setBaseFormat(validValue);

		EntityType entity = instancePersistence.getEntities().getEntityList()
				.get(0);
		validValue = entity.getClassName();
		String[] invalidClassNames = { "", "x" };
		for (String invalidClassName : invalidClassNames) {
			entity.setClassName(invalidClassName);
			validate(
					conf,
					INSTANCE_CONF_PATH.toString(),
					"LLRPServerInstanceConfiguration.xml:/LLRPServerInstanceConfiguration/persistence/entities/entity/className");
		}
		entity.setClassName(validValue);

		JSONType json = entity.getOutput().getFile().getFileProperties()
				.getType().getJSON();
		validValue = json.getMixInClassName();
		json.setMixInClassName("huhu");
		validate(
				conf,
				INSTANCE_CONF_PATH.toString(),
				"LLRPServerInstanceConfiguration.xml:/LLRPServerInstanceConfiguration/persistence/entities/entity/output/file/type/JSON/mixInClassName");
		json.setMixInClassName(validValue);

		groupId = entity.getGroupId();
		validValue = groupId.getBaseFormat();
		String[] invalidBaseFormats2 = { "", "x" };
		for (String invalidBaseFormat : invalidBaseFormats2) {
			groupId.setBaseFormat(invalidBaseFormat);
			validate(
					conf,
					INSTANCE_CONF_PATH.toString(),
					"LLRPServerInstanceConfiguration.xml:/LLRPServerInstanceConfiguration/persistence/entities/entity/groupId/baseFormat");
		}
		groupId.setBaseFormat(validValue);

		// remove optional JSON MixIn class name
		entity.getOutput().getFile().getFileProperties().getType().getJSON()
				.setMixInClassName(null);
		cv.validate(conf, INSTANCE_CONF_PATH.toString());

		// remove optional entities
		conf.getPersistence().setEntities(null);
		cv.validate(conf, INSTANCE_CONF_PATH.toString());
	}

	private void validate(LLRPServerConfigurationType conf, String filePath,
			String errorMessageEnd) {
		try {
			new ConfigurationValidator().validate(conf, filePath);
			Assert.fail();
		} catch (ConfigurationException e) {
			Assert.assertTrue(e.getMessage().endsWith(errorMessageEnd));
		}
	}

	private void validate(LLRPServerInstanceConfigurationType conf,
			String filePath, String errorMessageEnd) {
		try {
			new ConfigurationValidator().validate(conf, filePath);
			Assert.fail();
		} catch (ConfigurationException e) {
			Assert.assertTrue(e.getMessage().endsWith(errorMessageEnd));
		}
	}
}
