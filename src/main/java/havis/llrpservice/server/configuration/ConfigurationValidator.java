package havis.llrpservice.server.configuration;

import havis.device.rf.RFDevice;
import havis.llrpservice.xml.configuration.EntitiesType;
import havis.llrpservice.xml.configuration.EntityType;
import havis.llrpservice.xml.configuration.FileType;
import havis.llrpservice.xml.configuration.GroupIdType;
import havis.llrpservice.xml.configuration.InstanceInterfacesType;
import havis.llrpservice.xml.configuration.InstancePersistenceType;
import havis.llrpservice.xml.configuration.InstanceRFCType;
import havis.llrpservice.xml.configuration.JSONType;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;
import havis.llrpservice.xml.configuration.PersistenceType;
import havis.llrpservice.xml.configuration.ReflectionType;
import havis.llrpservice.xml.configuration.RfcPortProperties;
import havis.llrpservice.xml.configuration.ServerPersistenceType;

import java.text.SimpleDateFormat;
import java.util.regex.Pattern;

/**
 * The ConfigurationValidator validates LLRP configurations.
 */
public class ConfigurationValidator {

	private Pattern schemaVersionPattern = Pattern.compile("^\\d+(\\.\\d+)?$");
	private Pattern instanceIdPattern = Pattern.compile("^[a-zA-Z0-9_]+$");

	/**
	 * Validates a server configuration.
	 * 
	 * @param config
	 *            content of a configuration file
	 * @param filePath
	 *            the path to the configuration file
	 * @throws ConfigurationException
	 */
	public void validate(LLRPServerConfigurationType config, String filePath)
			throws ConfigurationException {
		if (config == null) {
			return;
		}
		validateSchemaVersion(filePath
				+ ":/LLRPServerConfiguration/schemaVersion",
				config.getSchemaVersion());
		ReflectionType reflection = config.getDefaults().getInterfaces()
				.getRFC().getRfcPortProperties().getReflection();
		if (reflection != null) {
			// rfc.controllerClassName
			validateRFDeviceClassName(
					filePath
							+ ":/LLRPServerConfiguration/defaults/interfaces/RFC/reflection/controllerClassName",
					reflection.getControllerClassName());
		}
		PersistenceType persistence = config.getDefaults().getPersistence();
		if (persistence != null) {
			// groupId.baseFormat
			validateGroupIdBaseFormat(
					filePath
							+ ":/LLRPServerConfiguration/defaults/persistence/groupId/baseFormat",
					persistence.getGroupId().getBaseFormat());
			validate(filePath
					+ ":/LLRPServerConfiguration/defaults/persistence",
					persistence.getEntities());
		}
		ServerPersistenceType serverPersistence = config.getPersistence();
		if (serverPersistence != null) {
			GroupIdType groupId = serverPersistence.getGroupId();
			if (groupId != null) {
				// groupId.baseFormat
				validateGroupIdBaseFormat(
						filePath
								+ ":/LLRPServerConfiguration/persistence/groupId/baseFormat",
						groupId.getBaseFormat());
			}
			validate(filePath + ":/LLRPServerConfiguration/persistence",
					serverPersistence.getEntities());
		}
	}

	/**
	 * Validates a server instance configuration.
	 * 
	 * @param config
	 *            content of a configuration file
	 * @param filePath
	 *            the path to the configuration file
	 * @throws ConfigurationException
	 */
	public void validate(LLRPServerInstanceConfigurationType config,
			String filePath) throws ConfigurationException {
		if (config == null) {
			return;
		}
		validateSchemaVersion(filePath
				+ ":/LLRPServerInstanceConfiguration/schemaVersion",
				config.getSchemaVersion());
		if (!instanceIdPattern.matcher(config.getInstanceId()).matches()) {
			throw new ConfigurationException("Invalid instance identifier '"
					+ config.getInstanceId() + "' at " + filePath
					+ ":/LLRPServerInstanceConfiguration/instanceId");
		}
		InstanceInterfacesType interfaces = config.getInterfaces();
		if (interfaces != null) {
			InstanceRFCType rfc = interfaces.getRFC();
			if (rfc != null) {
				RfcPortProperties rfcProperties = rfc.getRfcPortProperties();
				if (rfcProperties != null) {
					ReflectionType reflection = rfcProperties.getReflection();
					if (reflection != null) {
						// rfc.controllerClassName
						validateRFDeviceClassName(
								filePath
										+ ":/LLRPServerInstanceConfiguration/interfaces/RFC/reflection/controllerClassName",
								reflection.getControllerClassName());
					}
				}
			}
		}
		InstancePersistenceType persistence = config.getPersistence();
		if (persistence != null) {
			GroupIdType groupId = persistence.getGroupId();
			if (groupId != null) {
				// groupId.baseFormat
				validateGroupIdBaseFormat(
						filePath
								+ ":/LLRPServerInstanceConfiguration/persistence/groupId/baseFormat",
						groupId.getBaseFormat());
			}
			validate(
					filePath + ":/LLRPServerInstanceConfiguration/persistence",
					persistence.getEntities());
		}
	}

	private void validate(String path, EntitiesType entities)
			throws ConfigurationException {
		if (entities == null) {
			return;
		}
		String entityClassNamePath = path + "/entities/entity/className";
		String jsonMixInClassNamePath = path
				+ "/entities/entity/output/file/type/JSON/mixInClassName";
		String groupIdBaseFormatPath = path
				+ "/entities/entity/groupId/baseFormat";
		for (EntityType entity : entities.getEntityList()) {
			// className
			validateClassName(entityClassNamePath, entity.getClassName());
			FileType file = entity.getOutput().getFile();
			if (file != null) {
				JSONType json = file.getFileProperties().getType().getJSON();
				if (json != null) {
					// JSON MixIn class name
					validateClassName(jsonMixInClassNamePath,
							json.getMixInClassName());
				}
			}
			// groupId.baseFormat
			GroupIdType groupId = entity.getGroupId();
			if (groupId != null) {
				validateGroupIdBaseFormat(groupIdBaseFormatPath,
						groupId.getBaseFormat());
			}
		}
	}

	private Class<?> validateClassName(String path, String className)
			throws ConfigurationException {
		if (className == null) {
			return null;
		}
		try {
			return Class.forName(className.trim());
		} catch (ClassNotFoundException e) {
			throw new ConfigurationException("Unknown class name '" + className
					+ "' at " + path);
		}

	}

	private void validateRFDeviceClassName(String path, String className)
			throws ConfigurationException {
		Class<?> cl = validateClassName(path, className);
		Class<?>[] interfaces = cl.getInterfaces();
		boolean found = false;
		for (Class<?> i : interfaces) {
			if (i.equals(RFDevice.class)) {
				found = true;
				break;
			}
		}
		if (!found) {
			throw new ConfigurationException("Class " + className
					+ " does not implement the interface "
					+ RFDevice.class.getName() + " at " + path);
		}
	}

	private void validateGroupIdBaseFormat(String path, String baseFormat)
			throws ConfigurationException {
		if (baseFormat.trim().length() == 0) {
			throw new ConfigurationException("Invalid base format '"
					+ baseFormat + "' at " + path);
		}
		try {
			new SimpleDateFormat(baseFormat);
		} catch (IllegalArgumentException e) {
			throw new ConfigurationException("Invalid base format '"
					+ baseFormat + "' at " + path);
		}
	}

	private void validateSchemaVersion(String path, String schemaVersion)
			throws ConfigurationException {
		if (schemaVersion == null) {
			return;
		}
		if (!schemaVersionPattern.matcher(schemaVersion).matches()) {
			throw new ConfigurationException(
					"Invalid format of schema version '" + schemaVersion
							+ "' at " + path);
		}
	}
}
