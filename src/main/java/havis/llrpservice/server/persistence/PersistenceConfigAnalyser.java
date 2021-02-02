package havis.llrpservice.server.persistence;

import havis.llrpservice.xml.configuration.CleanUpType;
import havis.llrpservice.xml.configuration.EntitiesType;
import havis.llrpservice.xml.configuration.EntityType;
import havis.llrpservice.xml.configuration.GroupIdType;
import havis.llrpservice.xml.configuration.InstancePersistenceType;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;
import havis.llrpservice.xml.configuration.OutputType;
import havis.llrpservice.xml.configuration.PersistTimesType;
import havis.llrpservice.xml.configuration.PersistenceType;
import havis.llrpservice.xml.configuration.ServerPersistenceType;

import java.nio.file.Paths;

import com.rits.cloning.Cloner;

/**
 * Analyzes a server configuration and an server instance configuration for
 * persistence properties. Properties from the server instance configuration
 * have higher priority then from the server configuration.
 */
class PersistenceConfigAnalyser {

	private PersistenceType defaultPersistence;
	private ServerPersistenceType serverPersistence;
	private InstancePersistenceType instancePersistence;
	private String instanceConfigDir = "";
	private String serverConfigDir = "";

	/**
	 * @param serverConfig
	 * @param configDir
	 *            the absolute path to the directory with the file containing
	 *            the given configuration
	 */
	public void setServerConfig(LLRPServerConfigurationType serverConfig,
			String configDir) {
		if (serverConfig == null) {
			defaultPersistence = null;
			serverPersistence = null;
		} else {
			defaultPersistence = serverConfig.getDefaults().getPersistence();
			serverPersistence = serverConfig.getPersistence();
		}
		serverConfigDir = configDir;
	}

	/**
	 * @param serverInstanceConfig
	 * @param configDir
	 *            the absolute path to the directory with the file containing
	 *            the given configuration
	 */
	public void setServerInstanceConfig(
			LLRPServerInstanceConfigurationType serverInstanceConfig,
			String configDir) {
		instancePersistence = serverInstanceConfig == null ? null
				: serverInstanceConfig.getPersistence();
		instanceConfigDir = configDir;
	}

	/**
	 * Gets the output configuration part for a class from the configurations.
	 * 
	 * @param className
	 * @return
	 */
	public OutputType getOutput(String className) {
		OutputType result = null;
		String configDir = serverConfigDir;
		if (instancePersistence != null) {
			configDir = instanceConfigDir;
			EntityType entity = getEntity(instancePersistence.getEntities(),
					className);
			if (entity != null) {
				result = entity.getOutput();
			}
		} else if (serverPersistence != null) {
			EntityType entity = getEntity(serverPersistence.getEntities(),
					className);
			if (entity != null) {
				result = entity.getOutput();
			}
		}
		if (result == null && defaultPersistence != null) {
			EntityType entity = getEntity(defaultPersistence.getEntities(),
					className);
			if (entity != null) {
				result = entity.getOutput();
			}
		}
		if (result != null) {
			String baseDir = result.getFile().getFileProperties().getBaseDir()
					.trim();
			// if the configured base dir is relative
			if (!Paths.get(baseDir).isAbsolute()) {
				// clone the result for modification
				result = new Cloner().deepClone(result);
				// the path is relative to the configuration directory =>
				// join them
				String absBaseDir = Paths.get(configDir, baseDir).toString();
				result.getFile().getFileProperties().setBaseDir(absBaseDir);
			}
		}
		return result;
	}

	/**
	 * Gets the group configuration part for a class from the configurations.
	 * 
	 * @param className
	 * @return
	 */
	public GroupIdType getGroupId(String className) {
		GroupIdType result = null;
		if (instancePersistence != null) {
			EntityType entity = getEntity(instancePersistence.getEntities(),
					className);
			if (entity != null) {
				result = entity.getGroupId();
			}
			if (result == null) {
				result = instancePersistence.getGroupId();
			}
		} else if (serverPersistence != null) {
			EntityType entity = getEntity(serverPersistence.getEntities(),
					className);
			if (entity != null) {
				result = entity.getGroupId();
			}
			if (result == null) {
				result = serverPersistence.getGroupId();
			}
		}
		if (result == null && defaultPersistence != null) {
			EntityType entity = getEntity(defaultPersistence.getEntities(),
					className);
			if (entity != null) {
				result = entity.getGroupId();
			}
			if (result == null) {
				result = defaultPersistence.getGroupId();
			}
		}
		return result;
	}

	/**
	 * Gets the times configuration part for a class from the configurations.
	 * 
	 * @param className
	 * @return
	 */
	public PersistTimesType getTimes(String className) {
		PersistTimesType result = null;
		if (instancePersistence != null) {
			EntityType entity = getEntity(instancePersistence.getEntities(),
					className);
			if (entity != null) {
				result = entity.getTimes();
			}
			if (result == null) {
				result = instancePersistence.getTimes();
			}
		} else if (serverPersistence != null) {
			EntityType entity = getEntity(serverPersistence.getEntities(),
					className);
			if (entity != null) {
				result = entity.getTimes();
			}
			if (result == null) {
				result = serverPersistence.getTimes();
			}
		}
		if (result == null && defaultPersistence != null) {
			EntityType entity = getEntity(defaultPersistence.getEntities(),
					className);
			if (entity != null) {
				result = entity.getTimes();
			}
			if (result == null) {
				result = defaultPersistence.getTimes();
			}
		}
		return result;
	}

	/**
	 * Gets the clean up configuration part for a class from the configurations.
	 * 
	 * @param className
	 * @return
	 */
	public CleanUpType getCleanUp(String className) {
		CleanUpType result = null;
		if (instancePersistence != null) {
			EntityType entity = getEntity(instancePersistence.getEntities(),
					className);
			if (entity != null) {
				result = entity.getCleanUp();
			}
			if (result == null) {
				result = instancePersistence.getCleanUp();
			}
		} else if (serverPersistence != null) {
			EntityType entity = getEntity(serverPersistence.getEntities(),
					className);
			if (entity != null) {
				result = entity.getCleanUp();
			}
			if (result == null) {
				result = serverPersistence.getCleanUp();
			}
		}
		if (result == null && defaultPersistence != null) {
			EntityType entity = getEntity(defaultPersistence.getEntities(),
					className);
			if (entity != null) {
				result = entity.getCleanUp();
			}
			if (result == null) {
				result = defaultPersistence.getCleanUp();
			}
		}
		return result;
	}

	/**
	 * Gets an entity configuration for a class from a list of entity
	 * configurations.
	 * 
	 * @param entities
	 * @param className
	 * @return
	 */
	private EntityType getEntity(EntitiesType entities, String className) {
		if (entities != null) {
			for (EntityType entity : entities.getEntityList()) {
				if (entity.getClassName().trim().equals(className)) {
					return entity;
				}
			}
		}
		return null;
	}
}
