package havis.llrpservice.common.io;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.Test;

public class DirectoryHandlerTest {
	private static final String BASE_DIR = "havis/llrpservice/common/io/directoryHandler";
	private static final String JAR_CONTENT_RECURSIVE = "havis/device/";
	private static final String JAR_CONTENT_NON_RECURSIVE = Paths.get(
			JAR_CONTENT_RECURSIVE, "io", "exception").toString();

	@Test
	public void getDirectories() throws Exception {
		DirectoryHandler dirs = new DirectoryHandler();

		// without base dir
		List<Path> subdirs = dirs.getDirectories(null, true /* recursive */);
		Assert.assertEquals(subdirs.size(), 0);

		// non-existing base dir
		subdirs = dirs
				.getDirectories(Paths.get(BASE_DIR + "x"), true /* recursive */);
		Assert.assertEquals(subdirs.size(), 0);

		// non-recursive dir scan
		subdirs = dirs
				.getDirectories(Paths.get(BASE_DIR), false /* recursive */);
		Assert.assertEquals(subdirs.size(), 2);

		// recursive dir scan
		subdirs = dirs
				.getDirectories(Paths.get(BASE_DIR), true /* recursive */);
		Assert.assertEquals(subdirs.size(), 4);
	}

	@Test
	public void getJARDirectories() throws Exception {
		DirectoryHandler dirs = new DirectoryHandler();

		// non-existing base dir
		List<Path> subdirs = dirs
				.getDirectories(
						new PathHandler().toAbsolutePath(JAR_CONTENT_RECURSIVE)
								.resolve("a"), false /* recursive */);
		Assert.assertEquals(subdirs.size(), 0);

		// successful dir scan but without result
		subdirs = dirs.getDirectories(
				new PathHandler().toAbsolutePath(JAR_CONTENT_NON_RECURSIVE)
						.resolve("io/exception"), false /* recursive */);
		Assert.assertEquals(subdirs.size(), 0);

		// non-recursive dir scan
		subdirs = dirs
				.getDirectories(
						new PathHandler().toAbsolutePath(JAR_CONTENT_RECURSIVE),
						false /* recursive */);
		// */havis.device.io.interface-*.jar!/havis/device/io
		Assert.assertEquals(subdirs.size(), 1);

		// recursive dir scan
		subdirs = dirs
				.getDirectories(
						new PathHandler().toAbsolutePath(JAR_CONTENT_RECURSIVE),
						true /* recursive */);
		// */havis.device.io.interface-*.jar!/havis/device/io
		// */havis.device.io.interface-*.jar!/havis/device/io/exception
		Assert.assertEquals(subdirs.size(), 2);
	}
}
