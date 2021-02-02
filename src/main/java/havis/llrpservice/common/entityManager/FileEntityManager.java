package havis.llrpservice.common.entityManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Properties;
import java.util.UUID;

import com.rits.cloning.Cloner;

/**
 * This class is a implementation of {@link EntityManager}, which provides file
 * based entity management.
 * <p>
 * After creation of an instance, it must be opened with {@link #open()}. It
 * creates a storage directory given by the {@link FileProperty#BASEDIR}
 * parameter with a {@code .meta} file. If the folder already exists the
 * {@code .meta} file must contains the current informations.
 * </p>
 * <p>
 * The {@code .meta} file contains the class name of the persistable entity
 * objects, the class version and the content format like {@code XML}. The meta
 * data are stored in the JAVA property format with the keys {@code className},
 * {@code classVersion} and {@code contentFormat}.
 * </p>
 * <p>
 * The groups with the entities are stored in separate sub directories. The
 * group identifier is used as directory name. Each entity object is stored in a
 * separate file. The entity identifier is used as file name.
 * </p>
 * <p>
 * This class provides abstract methods for de-/serialization. So it is possible
 * to use different serializers to flush the content to disk.
 * </p>
 * 
 * @param <T>
 *            class type
 */
public abstract class FileEntityManager<T> implements EntityManager<T> {

	private static final Logger log = Logger.getLogger(FileEntityManager.class.getName());

	Pattern classVersionPattern = Pattern.compile("(\\d+)(\\.\\d+(\\.\\d+)?)?");

	public enum FileProperty {
		/**
		 * The base directory of the storage
		 */
		BASEDIR
	};

	private class InternalEntity {
		// entity object
		private T obj;
		// whether the object is synchronized with the storage
		private boolean isSynchronized = false;

		public InternalEntity(T obj) {
			this.obj = obj;
		}
	}

	private final Path basePath;
	private final Class<T> clazz;
	private final String classVersion;
	private Map<String, InternalEntity> entities = new HashMap<>();
	private Cloner cloner = new Cloner();

	/**
	 * 
	 * @param clazz
	 * @param classVersion
	 *            the class version like <code>1.0</code>. The first part (here
	 *            the <code>1</code>) is the major version which must be changed
	 *            if a new version is not backward compatible.
	 * @param properties
	 * @throws MissingPropertyException
	 */
	public FileEntityManager(Class<T> clazz, String classVersion,
			Map<FileProperty, Object> properties)
			throws MissingPropertyException {
		// check for mandatory properties
		if (properties.containsKey(FileProperty.BASEDIR)) {
			basePath = Paths.get((String) properties.get(FileProperty.BASEDIR));
		} else {
			throw new MissingPropertyException("Missing property: "
					+ FileProperty.BASEDIR.name());
		}

		this.clazz = clazz;
		this.classVersion = classVersion;
	}

	/**
	 * Creates an instance with initial entities.
	 * 
	 * @param clazz
	 * @param classVersion
	 * @param properties
	 * @param initialEntities
	 *            map of entityId to entity object
	 * @throws MissingPropertyException
	 */
	public FileEntityManager(Class<T> clazz, String classVersion,
			Map<FileProperty, Object> properties, Map<String, T> initialEntities)
			throws MissingPropertyException {
		this(clazz, classVersion, properties);
		if (initialEntities.size() > 0) {
			for (Entry<String, T> entity : initialEntities.entrySet()) {
				entities.put(entity.getKey(),
						new InternalEntity(entity.getValue()));
			}
			log.log(Level.FINE, "Initial entities added: " + initialEntities.keySet());
		}
	}

	/**
	 * Opens the entity manager.
	 * <p>
	 * The base directory of the storage with the meta file is created. If the
	 * meta file already exists the informations will be checked. If the
	 * informations are not compatible with the current informations a
	 * {@link WrongMetaDataException} is thrown. Class versions are compatible
	 * if the major versions are equal.
	 * </p>
	 * 
	 * @throws EntityManagerException
	 */
	@Override
	public synchronized void open() throws EntityManagerException {
		// create base directory
		File baseFile = basePath.toFile();
		if (!mkDirs(baseFile)) {
			throw new EntityManagerException("Creating of base directory "
					+ baseFile.getAbsolutePath() + " failed");
		}
		// create meta file with properties
		File metaFile = new File(baseFile, ".meta");
		Properties metaValues = new Properties();
		InputStream input = null;
		if (metaFile.exists()) {
			try {
				try {
					input = new FileInputStream(metaFile);
					metaValues.load(input);
				} finally {
					if (input != null) {
						input.close();
					}
				}
			} catch (Exception e) {
				throw new EntityManagerException(e);
			}
			String value = metaValues.getProperty("className");
			if (value == null || !(value.equals(clazz.getName()))) {
				throw new WrongMetaDataException("Incompatible class name "
						+ value + " for meta data value '" + clazz.getName()
						+ "'");
			}

			value = metaValues.getProperty("classVersion");
			if (value == null) {
				throw new WrongMetaDataException("Incompatible class version '"
						+ value + "' for meta data value '" + classVersion
						+ "'");
			}
			Matcher valueMatcher = classVersionPattern.matcher(value);
			Matcher m = classVersionPattern.matcher(classVersion);
			if (!valueMatcher.matches() || !m.matches()
					|| !valueMatcher.group(1).equals(m.group(1))) {
				throw new WrongMetaDataException("Incompatible class version '"
						+ value + "' for meta data value '" + classVersion
						+ "'");
			}

			value = metaValues.getProperty("contentFormat");
			if (value == null || !(value.equals(getContentFormat()))) {
				throw new WrongMetaDataException(
						"Incompatible content format '" + value
								+ "' for meta data value '"
								+ getContentFormat() + "'");
			}
		} else {
			metaValues.setProperty("className", clazz.getName());
			metaValues.setProperty("classVersion", classVersion);
			metaValues.setProperty("contentFormat", getContentFormat());
			OutputStream output = null;
			try {
				try {
					output = new FileOutputStream(metaFile);
					metaValues.store(output, null);
					log.log(Level.INFO, "Opened storage for " + metaValues.toString() + " in " +  metaFile.getAbsolutePath());
				} finally {
					if (output != null) {
						output.close();
					}
				}
			} catch (Exception e) {
				throw new EntityManagerException(e);
			}
		}
	}

	@Override
	public void close() {
	}

	@Override
	public synchronized List<String> add(List<T> entities) {
		List<String> entityIds = new ArrayList<String>();
		for (T entity : entities) {
			// Calculate entity ID
			String entityId = UUID.randomUUID().toString().replace("-", "");
			entityIds.add(entityId);
			// Store entity in entity map
			this.entities.put(entityId, new InternalEntity(entity));
		}
		if (entities.size() > 0) {
			log.log(Level.FINE, "Entities added: " + entityIds);
		}
		return entityIds;
	}

	@Override
	public synchronized List<T> remove(List<String> entityIds)
			throws UnknownEntityException {
		List<T> result = new ArrayList<T>();
		// check if entities are managed
		entitiesManaged(entityIds);
		for (String entityId : entityIds) {
			// Store the object temporally
			InternalEntity entity = entities.remove(entityId);
			result.add(entity.obj);
		}
		log.log(Level.FINE, "Entities removed: " + entityIds);
		return result;
	}

	@Override
	public synchronized List<Entity<T>> acquire(List<String> entityIds)
			throws UnknownEntityException {
		List<Entity<T>> result = new ArrayList<>();
		// check if entities are managed
		entitiesManaged(entityIds);
		for (String entityId : entityIds) {
			InternalEntity currentEntity = entities.get(entityId);
			// create a clone of the entity object
			T clone = cloner.deepClone(currentEntity.obj);
			// add an entity with a reference to the original object and a clone
			// to the result list
			Entity<T> entity = new Entity<>(entityId, currentEntity.obj, clone);
			result.add(entity);
		}
		log.log(Level.FINE, "Entities acquired: " + entityIds);
		return result;
	}

	@Override
	public synchronized void release(List<Entity<T>> entities, boolean write)
			throws UnknownEntityException, StaleEntityStateException {
		// check if entities are managed
		List<String> entityIds = new ArrayList<>();
		for (Entity<T> entity : entities) {
			entityIds.add(entity.getEntityId());
		}
		entitiesManaged(entityIds);
		// if entities shall be replaced
		if (write) {
			Map<InternalEntity, T> objClones = new HashMap<>();
			// for each entity
			for (Entity<T> entity : entities) {
				// get current entity
				InternalEntity currentEntity = this.entities.get(entity
						.getEntityId());
				// if the entity has not been changed since the entity has been
				// acquired
				if (entity.getSourceObject() == currentEntity.obj) {
					// save the clone of the current entity object
					objClones.put(currentEntity, entity.getObject());
				} else {
					throw new StaleEntityStateException(
							"Entity "
									+ entity.getEntityId()
									+ " cannot be replaced because the entity was already changed otherwise");
				}
			}
			for (Entry<InternalEntity, T> entry : objClones.entrySet()) {
				InternalEntity currentEntity = entry.getKey();
				T objClone = entry.getValue();
				// replace entity object with clone
				currentEntity.obj = objClone;
				// the entity is not synchronized with storage
				currentEntity.isSynchronized = false;
			}
		}
		log.log(Level.FINE, "Entities released: " + entityIds);
	}

	@Override
	public synchronized void flush(String groupId, List<String> entityIds)
			throws EntityManagerException {
		// check if entities are managed
		entitiesManaged(entityIds);
		File groupFile = new File(basePath.toFile(), groupId);
		// New Group or existing group
		boolean isNewGroup = false;
		// GroupDir exists under groupId
		if (groupFile.exists()) {
			// Delete entities which shall not be in the group any longer
			try {
				deleteEntityFiles(groupId, entityIds);
			} catch (IOException e) {
				throw new EntityManagerException(e);
			}
		} else {
			// create group dir
			if (!mkDir(groupFile)) {
				throw new EntityManagerException("Creating of group directory "
						+ groupFile + " failed");
			}
			isNewGroup = true;
		}
		for (String entity : entityIds) {
			InternalEntity currentEntity = entities.get(entity);
			// If entity should be stored in a new group or entity was
			// modified
			if (isNewGroup || !currentEntity.isSynchronized) {
				// Create file
				File entityFile = new File(groupFile, entity);
				// Get serialized object
				byte[] encoded;
				try {
					encoded = serialize(currentEntity.obj);
					// Write serialized content to storage
					Files.write(entityFile.toPath(), encoded);
				} catch (Exception e) {
					throw new EntityManagerException(e);
				}
				log.log(Level.INFO, "Wrote data of entity " + entity + " (" + encoded.length + " bytes) to file: " + entityFile.getAbsolutePath());
				currentEntity.isSynchronized = true;
			}
		}
	}

	@Override
	public synchronized List<String> refresh(String groupId)
			throws EntityManagerException {
		List<String> entityIds = new ArrayList<String>();
		File groupFile = new File(basePath.toFile(), groupId);
		Path groupPath = groupFile.toPath();
		if (!groupFile.exists()) {
			throw new UnknownGroupException("Unknown group: " + groupId);
		}
		// Walk through files in groupDir
		List<Path> files = new ArrayList<Path>();
		FileVisitor<Path> fileWalker = new FileWalker(groupPath,
				null /* directories */, files);
		try {
			Files.walkFileTree(groupPath, fileWalker);
			for (Path file : files) {
				// the file name is the entityId
				String entityId = file.getFileName().toString();
				// Save entityIds in groupDir
				entityIds.add(entityId);
				// Read file
				byte content[] = Files.readAllBytes(file);
				// Create new entity with deserialized object
				InternalEntity currentEntity = new InternalEntity(
						deserialize(content));
				// The local object and the stored file are synchronized
				currentEntity.isSynchronized = true;
				entities.put(entityId, currentEntity);
				log.log(Level.INFO, "Entity refreshed: " + entityId);
			}
		} catch (Exception e) {
			throw new EntityManagerException(e);
		}
		return entityIds;
	}

	@Override
	public synchronized List<EntityGroup> getGroups()
			throws EntityManagerException {
		List<EntityGroup> entityGroups = new ArrayList<EntityGroup>();
		// Walk through baseDir
		List<Path> directories = new ArrayList<Path>();
		FileVisitor<Path> fileWalker = new FileWalker(basePath, directories,
				null /* files */);
		try {
			Files.walkFileTree(basePath, fileWalker);
			for (Path dir : directories) {
				String dirName = dir.getFileName().toString();
				// ignore the baseDir and all dirs starting with "."
				if (!dirName.startsWith(".") && !dir.equals(basePath)) {
					EntityGroup entityGroup = new EntityGroup(dirName);
					BasicFileAttributes attr = Files.readAttributes(dir,
							BasicFileAttributes.class);
					entityGroup.setCreationDate(new Date(attr.creationTime()
							.toMillis()));
					entityGroups.add(entityGroup);
				}
			}
		} catch (IOException e) {
			throw new EntityManagerException(e);
		}
		return entityGroups;
	}

	@Override
	public synchronized void delete(String groupId)
			throws EntityManagerException {
		File groupFile = new File(basePath.toFile(), groupId);
		try {
			// Delete all entities in groupDir
			deleteEntityFiles(groupId, /* excludeEntities */null);
			// Delete groupDir
			Files.deleteIfExists(groupFile.toPath());
		} catch (IOException e) {
			throw new EntityManagerException(e);
		}
		log.log(Level.INFO, "Deleted group " + groupId + ":" + groupFile.getAbsolutePath());
	}

	/**
	 * Checks if entities are managed. If an entity is not managed an exception
	 * is thrown.
	 * 
	 * @param entityIds
	 * @throws UnknownEntityException
	 */
	private void entitiesManaged(List<String> entityIds)
			throws UnknownEntityException {
		for (String entityId : entityIds) {
			// If not entity exists
			if (!entities.containsKey(entityId)) {
				throw new UnknownEntityException("Entity unknown " + entityId);
			}
		}
	}

	/**
	 * Creates a directory.
	 * 
	 * @param dir
	 * @return True, if directory already exists, false otherwise
	 */
	private boolean mkDir(File dir) {
		if (dir.exists()) {
			return true;
		}
		int sleepCounter = 0;
		while (!dir.mkdir()) {
			// Safety
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
			}
			if (sleepCounter < 10) {
				sleepCounter++;
			} else {
				return false;
			}
		}
		wait(dir);
		return true;
	}

	/**
	 * Creates a directory including all parent directories.
	 * 
	 * @param dir
	 * @return True, if directory already exists, false otherwise
	 */
	private boolean mkDirs(File dir) {
		if (dir.exists()) {
			return true;
		}
		int sleepCounter = 0;
		while (!dir.mkdirs()) {
			// Safety
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
			}
			if (sleepCounter < 10) {
				sleepCounter++;
			} else {
				return false;
			}
		}
		wait(dir);
		return true;
	}

	/**
	 * Waits for the existence of a file/directory.
	 * 
	 * @param file
	 * @return True, if directory exists now, false otherwise
	 */
	private boolean wait(File file) {
		int sleepCounter = 0;
		while (!file.exists()) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
			}
			if (sleepCounter < 10) {
				sleepCounter++;
			} else {
				return false;
			}
		}
		return true;
	}

	/**
	 * Delete entity files of a group.
	 * 
	 * @param groupId
	 * @param excludeEntityIds
	 *            Entities, which should not be deleted. If this value is set to
	 *            null, all entities in the group will be deleted
	 * @throws UnknownGroupException
	 * @throws IOException
	 */
	private void deleteEntityFiles(String groupId, List<String> excludeEntityIds)
			throws UnknownGroupException, IOException {
		File groupFile = new File(basePath.toFile(), groupId);
		Path groupPath = groupFile.toPath();
		// Check, if the groupDir exists
		if (!groupFile.exists()) {
			throw new UnknownGroupException("Unknown group: " + groupId);
		}
		// Walk through files
		List<Path> files = new ArrayList<Path>();
		FileVisitor<Path> fileWalker = new FileWalker(groupPath,
				null /* directories */, files);
		Files.walkFileTree(groupPath, fileWalker);
		for (Path file : files) {
			// the file name is the entityId
			String entityId = file.getFileName().toString();
			// If exclude-filter is null or entity is not in exclude-filter
			if (excludeEntityIds == null
					|| !excludeEntityIds.contains(entityId)) {
				Files.delete(file);
				// if it has been deleted but is managed yet then it is not
				// synchronized any longer
				InternalEntity entity = entities.get(entityId);
				if (entity != null) {
					entity.isSynchronized = false;
				}
			}
		}
	}

	/**
	 * Serializes an object to a byte array.
	 * 
	 * @param obj
	 * @return The serialized bytes
	 * @throws Exception
	 */
	abstract byte[] serialize(T obj) throws Exception;

	/**
	 * De-serializes a byte array to an object.
	 * 
	 * @param obj
	 * @return The de-serialized object
	 * @throws Exception
	 */
	abstract T deserialize(byte[] obj) throws Exception;

	abstract String getContentFormat();

	/**
	 * Collects the paths to the direct sub directories and its containing files
	 * of a given base directory.
	 */
	private class FileWalker extends SimpleFileVisitor<Path> {
		private Path baseDir;
		private List<Path> subDirs;
		private List<Path> files;

		public FileWalker(Path baseDir, List<Path> subDirs, List<Path> files) {
			this.baseDir = baseDir;
			this.subDirs = subDirs;
			this.files = files;
		}

		@Override
		public FileVisitResult preVisitDirectory(Path directory,
				BasicFileAttributes attrs) throws IOException {
			if (subDirs != null) {
				subDirs.add(directory);
			}
			// skip the scanning of further sub dirs if the current dir is NOT
			// the base dir
			// (if the baseDir is absolute/relative then the given directory is
			// also absolute/relative)
			return directory.equals(baseDir) ? FileVisitResult.CONTINUE
					: FileVisitResult.SKIP_SUBTREE;
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
				throws IOException {
			if (files != null) {
				files.add(file);
			}
			return FileVisitResult.CONTINUE;
		}
	}
}
