package havis.llrpservice.common.io;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class DirectoryHandler {
	/**
	 * Gets the sub directories of a given root directory. A relative root path
	 * starts at the class path.
	 * 
	 * @param rootDirectory
	 * @param recursive
	 * @return list of absolute paths
	 * @throws IOException
	 */
	public List<Path> getDirectories(Path rootDirectory, final boolean recursive)
			throws IOException {
		final List<Path> ret = new ArrayList<>();
		PathHandler ph = new PathHandler();
		final Path root = ph.toAbsolutePath(rootDirectory);
		if (root == null) {
			return ret;
		}
		Path jarContentPath = ph.getJARContentPath(root);
		if (jarContentPath == null) {
			Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir,
						BasicFileAttributes attrs) throws IOException {
					if (dir.equals(root)) {
						return FileVisitResult.CONTINUE;
					}
					ret.add(dir);
					return recursive ? FileVisitResult.CONTINUE
							: FileVisitResult.SKIP_SUBTREE;
				};
			});
		} else {
			String jarContentPathStr = jarContentPath.toString();
			Enumeration<URL> en = getClass().getClassLoader().getResources(
					jarContentPathStr);
			if (en.hasMoreElements()) {
				URL url = en.nextElement();
				JarURLConnection urlcon = (JarURLConnection) (url
						.openConnection());
				try (JarFile jar = urlcon.getJarFile();) {
					Enumeration<JarEntry> entries = jar.entries();
					while (entries.hasMoreElements()) {
						// get entry (name of directories end with "/")
						JarEntry entry = entries.nextElement();
						Path entryPath = Paths.get(entry.getName());
						// if directory + required root path + NOT the root path
						// itself
						if (entry.isDirectory()
								&& entryPath.startsWith(jarContentPath)
								&& !entryPath.equals(jarContentPath)) {
							Path subDir = entryPath;
							if (!recursive) {
								while (!subDir.getParent().equals(
										jarContentPath)) {
									subDir = entryPath.getParent();
								}
							}
							Path absSubDir = ph.toAbsolutePath(subDir);
							if (!ret.contains(absSubDir)) {
								ret.add(absSubDir);
							}
						}
					}
				}
			}
		}
		return ret;
	}
}
