package havis.llrpservice.common.entityManager;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class _FileHelperTest {

	/**
	 * Get List of files from a directory recursively
	 * 
	 * @param dir
	 * @return
	 * @throws IOException
	 */
	public static List<File> getFiles(String dir) throws IOException {
		List<File> files = new ArrayList<File>();
		List<File> dirs = new ArrayList<File>();
		FileVisitor<Path> fileWalker = new FileWalker(files, dirs);
		Files.walkFileTree(Paths.get(dir), fileWalker);
		return files;
	}

	/**
	 * Get List of dirs from a directory recursively
	 * 
	 * @param dir
	 * @return
	 * @throws IOException
	 */
	public static List<File> getDirs(String dir) throws IOException {
		List<File> files = new ArrayList<File>();
		List<File> dirs = new ArrayList<File>();
		FileVisitor<Path> fileWalker = new FileWalker(files, dirs);
		Files.walkFileTree(Paths.get(dir), fileWalker);
		return dirs;
	}

	/**
	 * Delete Directory recursively
	 * 
	 * @param dir
	 *            absolute path-string
	 * @throws IOException
	 */
	public static void deleteFiles(String dir) throws IOException {
		List<File> files = new ArrayList<File>();
		List<File> dirs = new ArrayList<File>();
		FileVisitor<Path> fileWalker = new FileWalker(files, dirs);
		Files.walkFileTree(Paths.get(dir), fileWalker);

		for (File file : files) {
			file.delete();
		}

		Collections.reverse(dirs);
		for (File directory : dirs) {
			File file = directory;
			if (file.isDirectory() && !Paths.get(dir).equals(file.toPath())) {
				if (file.list().length == 0) {
					Files.delete(file.toPath());
				}
			}
		}
	}

	/**
	 * Read content from a file
	 * 
	 * @param path
	 *            absolute path to file
	 * @return
	 * @throws IOException
	 */
	public static String readFile(String path) throws IOException {
		return readFile(path, StandardCharsets.UTF_8);
	}

	/**
	 * Read content from a file
	 * 
	 * @param path
	 *            absolute path to file
	 * @param encoding
	 * @return
	 * @throws IOException
	 */
	public static String readFile(String path, Charset encoding)
			throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return encoding.decode(ByteBuffer.wrap(encoded)).toString();
	}

	/**
	 * Write content to file
	 * 
	 * @param path
	 * @param content
	 * @throws IOException
	 */
	public static void writeFile(String path, String content)
			throws IOException {
		writeFile(path, content, StandardCharsets.UTF_8);
	}

	/**
	 * Write content to a file
	 * 
	 * @param path
	 *            absolute path to file
	 * @param encoding
	 * @return
	 * @throws IOException
	 */
	public static void writeFile(String path, String content, Charset encoding)
			throws IOException {
		byte[] encoded = content.getBytes(encoding);
		Files.write(Paths.get(path), encoded);
	}

	public static long waitForFullSeconds() throws InterruptedException {
		long wait = System.currentTimeMillis() % 1000;
		Thread.sleep(1000 - wait + 20);
		return wait;
	}

	/**
	 * 
	 * FileVisitor implementation
	 * 
	 */
	private static class FileWalker extends SimpleFileVisitor<Path> {

		private List<File> directories;
		private List<File> files;

		public FileWalker(List<File> files, List<File> directories) {
			this.files = files;
			this.directories = directories;
		}

		@Override
		public FileVisitResult preVisitDirectory(Path directory,
				BasicFileAttributes attrs) throws IOException {
			directories.add(directory.toFile());
			return FileVisitResult.CONTINUE;
		}

		@Override
		public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
				throws IOException {
			files.add(file.toFile());
			return FileVisitResult.CONTINUE;
		}

	}

}
