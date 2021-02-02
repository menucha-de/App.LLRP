package havis.llrpservice.common.io;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.testng.Assert;
import org.testng.annotations.Test;

@Test
public class PathHandlerTest {

	private static final String BASE_DIR = "havis/llrpservice/common/io/pathHandler";
	private static String JAR_CONTENT = "testng-1.0.dtd";
	private static final String FILE = "file.txt";

	public void toAbsolutePath() {
		PathHandler pr = new PathHandler();

		Assert.assertNull(pr.toAbsolutePath((Path) null));
		Assert.assertNull(pr.toAbsolutePath((String) null));

		// existing rel. path to a directory
		Assert.assertFalse(Paths.get(BASE_DIR).isAbsolute());
		Path baseDirAbsPath = pr.toAbsolutePath(BASE_DIR);
		Assert.assertTrue(baseDirAbsPath.isAbsolute());
		// existing abs. path to a directory
		Assert.assertEquals(pr.toAbsolutePath(baseDirAbsPath), baseDirAbsPath);
		Assert.assertEquals(pr.toAbsolutePath(baseDirAbsPath.toString()),
				baseDirAbsPath);
		// non-existing rel. path to a directory
		Assert.assertNull(pr.toAbsolutePath(Paths.get(BASE_DIR + "x")));
		Assert.assertNull(pr.toAbsolutePath(BASE_DIR + "x"));
		// non-existing abs. path to a directory
		Assert.assertNull(pr.toAbsolutePath(Paths.get(baseDirAbsPath + "x")));
		Assert.assertNull(pr.toAbsolutePath(baseDirAbsPath + "x"));

		// existing rel. path to a file
		baseDirAbsPath = pr.toAbsolutePath(Paths.get(BASE_DIR).resolve(FILE));
		Assert.assertTrue(baseDirAbsPath.isAbsolute());
		// existing abs. path to a file
		Assert.assertEquals(pr.toAbsolutePath(baseDirAbsPath), baseDirAbsPath);
		// non-existing rel. path to a file
		Assert.assertNull(pr.toAbsolutePath(Paths.get(BASE_DIR).resolve(FILE)
				+ "x"));
		// non-existing abs. path to a file
		Assert.assertNull(pr.toAbsolutePath(baseDirAbsPath + "x"));

		// existing rel. path to JAR content
		Assert.assertFalse(Paths.get(JAR_CONTENT).isAbsolute());
		Path jarContentAbsPath = pr.toAbsolutePath(JAR_CONTENT);
		Assert.assertTrue(jarContentAbsPath.isAbsolute());
		Assert.assertTrue(jarContentAbsPath.toString().matches(".*jar!.*"));
		// existing abs. path to JAR content
		Assert.assertEquals(pr.toAbsolutePath(jarContentAbsPath),
				jarContentAbsPath);
		Assert.assertEquals(pr.toAbsolutePath(jarContentAbsPath.toString()),
				jarContentAbsPath);
		// non-existing rel. path to JAR content
		Assert.assertNull(pr.toAbsolutePath(Paths.get(JAR_CONTENT + "x")));
		Assert.assertNull(pr.toAbsolutePath(JAR_CONTENT + "x"));
		// non-existing abs. path to JAR content
		Assert.assertNull(pr.toAbsolutePath(Paths.get(jarContentAbsPath + "x")));
		Assert.assertNull(pr.toAbsolutePath(jarContentAbsPath + "x"));
	}

	public void getJARContentPath() throws Exception {
		// existing path to JAR content
		Path absJarContentPath = new PathHandler().toAbsolutePath(JAR_CONTENT);
		Assert.assertTrue(absJarContentPath.toString().matches(".*jar!.*"));
		Path contentPath = new PathHandler()
				.getJARContentPath(absJarContentPath);		
		Assert.assertEquals(contentPath.toString(), JAR_CONTENT);
		Assert.assertFalse(contentPath.isAbsolute());
		contentPath = new PathHandler().getJARContentPath(absJarContentPath
				.toString());
		Assert.assertEquals(contentPath.toString(), JAR_CONTENT);
		Assert.assertFalse(contentPath.isAbsolute());

		// non-existing path to JAR content
		Assert.assertNull(new PathHandler().getJARContentPath(absJarContentPath
				.resolve("x")));

		// existing rel. path to a directory
		Assert.assertNull(new PathHandler().getJARContentPath(Paths
				.get(BASE_DIR)));
		Assert.assertNull(new PathHandler().getJARContentPath(BASE_DIR));
	}
}
