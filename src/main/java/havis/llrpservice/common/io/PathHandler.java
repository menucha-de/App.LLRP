package havis.llrpservice.common.io;

import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PathHandler {
	private final static String DUMMY_JAR = "/ahB8Iu2uphai9Ulikei1PhaeiegaeR2e.jar";
	private final static char DELIMITER = '!';

	/**
	 * Resolves a path to an absolute path. A relative path starts at the class
	 * path.
	 * 
	 * @param path
	 * @return The absolute path or <code>null</code> if the path does not exist
	 */
	public Path toAbsolutePath(Path path) {
		if (path == null) {
			return null;
		}
		if (path.isAbsolute()) {
			return getJARContentPath(path) != null || Files.exists(path) ? path
					: null;
		}
		URL resource = getClass().getClassLoader().getResource(path.toString());
		if (resource == null) {
			return null;
		}
		// file:/any/path
		// jar:file:/a/path/a.jar!/any/file

		String resourceStr;
		switch (resource.getProtocol()) {
		case "jar":
			resourceStr = resource.getFile();
			break;
		case "bundle":
			resourceStr = "file:" + DUMMY_JAR + DELIMITER + resource.getFile();
			break;
		default:
			resourceStr = resource.toString();
		}
		// file:/any/path
		// file:/a/path/a.jar!/any/file

		// the URL contains an absolute path BUT Paths.get detects an relative
		// path (Path.isAbsolute method returns false) => convert it to an URI
		// first

		// /any/path
		// /a/path/a.jar!/any/file
		return Paths.get(URI.create(resourceStr));
	}

	/**
	 * See {@link #toAbsolutePath(Path)}
	 * 
	 * @param path
	 * @return The absolute path or null
	 */
	public Path toAbsolutePath(String path) {
		if (path == null) {
			return null;
		}
		return toAbsolutePath(Paths.get(path));
	}

	/**
	 * Returns the path to JAR content as relative path to the root of a JAR.
	 * With a given path <code>/a/path/a.jar!/any/file</code> the path
	 * <code>any/file</code> is returned.
	 * 
	 * @param path
	 * @return The relative path to the JAR file or <code>null</code> if the
	 *         path does not exist
	 */
	public Path getJARContentPath(Path path) {
		if (path == null) {
			return null;
		}
		String pathStr = path.toString();
		// get position of first delimiter
		int i = pathStr.indexOf(DELIMITER);
		// while a delimiter is found in path
		while (i >= 0) {
			// if path ends with a delimiter
			if (i + 1 == pathStr.length()) {
				// its a file/directory with the delimiter in its name
				return null;
			}
			String jarFile = pathStr.substring(0, i);
			// if the part of the path before the delimiter is a regular file
			// (the delimiter could also be part of a file/directory name)
			if (jarFile.equals(DUMMY_JAR)
					|| Files.isRegularFile(Paths.get(pathStr.substring(0, i)))) {
				// get relative content path (part of path after delimiter +
				// path separator)
				String contentPath = (i == pathStr.length() - 2) ? "" : pathStr
						.substring(i + 2);
				// if the content exists then return the relative path else
				// "null"
				return getClass().getClassLoader().getResource(contentPath) == null ? null
						: Paths.get(contentPath);
			}
			// get position of next delimiter
			i = pathStr.indexOf(DELIMITER, i + 1);
		}
		return null;
	}

	/**
	 * See {@link #getJARContentPath(Path)}
	 * 
	 * @param path
	 * @return The relative path to the JAR file or null
	 */
	public Path getJARContentPath(String path) {
		if (path == null) {
			return null;
		}
		return getJARContentPath(Paths.get(path));
	}
}
