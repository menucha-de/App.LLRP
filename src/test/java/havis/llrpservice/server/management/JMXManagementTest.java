package havis.llrpservice.server.management;

import havis.llrpservice.server.management.bean.Server;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import mockit.Mocked;

import org.testng.Assert;
import org.testng.annotations.Test;

public class JMXManagementTest {

	@Test
	public void register(@Mocked final Server mbean) throws Exception {
		checkInvalid(null, mbean);
		checkInvalid("", mbean);
		checkInvalid("/", mbean);
		checkInvalid("/a", mbean);

		checkValid("/a/b", mbean, "a:type=b");
		checkValid("/a/b/c", mbean, "a:type=b,name=c");
//		checkValid("a\"/\"b/c\"", mbean,
//				"\"a\\\"\":type=\"\\\"b\",name=\"c\\\"\"");

		checkInvalid("/a/b/c/d", mbean);
	}

	@Test
	public void unregister() throws Exception {
		JMXManagement mgmt = new JMXManagement();
		mgmt.open();
		try {
			mgmt.unregister("/a/b");
			Assert.fail();
		} catch (ManagementException e) {
			Assert.assertTrue(e.getMessage().contains(
					"Cannot unregister bean at '/a/b'"));
		}
		mgmt.close();
	}

	@Test
	public void close(@Mocked final Server mbean) throws Exception {
		// open a management instance and register multiple beans
		JMXManagement mgmt1 = new JMXManagement();
		mgmt1.open();
		mgmt1.register("/a/b", mbean);
		mgmt1.register("/a/b/c", mbean);
		MBeanServer beanServer = java.lang.management.ManagementFactory
				.getPlatformMBeanServer();
		Assert.assertTrue(beanServer.isRegistered(new ObjectName("a:type=b")));
		Assert.assertTrue(beanServer.isRegistered(new ObjectName(
				"a:type=b,name=c")));
		// open a second management instance and register a bean
		JMXManagement mgmt2 = new JMXManagement();
		mgmt2.open();
		mgmt2.register("/x/y", mbean);
		Assert.assertTrue(beanServer.isRegistered(new ObjectName("x:type=y")));
		// close the first management instance
		mgmt1.close();
		// the beans of the first management instance have been unregistered
		Assert.assertFalse(beanServer.isRegistered(new ObjectName("a:type=b")));
		Assert.assertFalse(beanServer.isRegistered(new ObjectName(
				"a:type=b,name=c")));
		Assert.assertTrue(beanServer.isRegistered(new ObjectName("x:type=y")));
		// close the second management instance
		mgmt2.close();
		// the bean of the second management instance have been unregistered
		Assert.assertFalse(beanServer.isRegistered(new ObjectName("x:type=y")));
	}

	/**
	 * Registers and unregisters a management bean using a valid path.
	 * 
	 * @param path
	 * @param mbean
	 * @param objectName
	 * @throws ManagementException
	 * @throws MalformedObjectNameException
	 */
	private void checkValid(String path, Object mbean, String objectName)
			throws ManagementException, MalformedObjectNameException {
		JMXManagement mgmt = new JMXManagement();
		mgmt.open();
		MBeanServer beanServer = java.lang.management.ManagementFactory
				.getPlatformMBeanServer();
		ObjectName objName = new ObjectName(objectName);
		mgmt.register(path, mbean);
		Assert.assertTrue(beanServer.isRegistered(objName));
		mgmt.unregister(path);
		Assert.assertFalse(beanServer.isRegistered(objName));
		mgmt.close();
	}

	/**
	 * Tries to register and unregister a management bean using an invalid path.
	 * 
	 * @param path
	 * @param mbean
	 * @throws ManagementException
	 * @throws MalformedObjectNameException
	 */
	private void checkInvalid(String path, Object mbean)
			throws ManagementException, MalformedObjectNameException {
		JMXManagement mgmt = new JMXManagement();
		mgmt.open();
		try {
			mgmt.register(path, mbean);
			Assert.fail();
		} catch (ManagementException e) {
			Assert.assertTrue(e.getMessage().contains(
					"Invalid path '" + path + "'"));
		}
		try {
			mgmt.unregister(path);
			Assert.fail();
		} catch (ManagementException e) {
			Assert.assertTrue(e.getMessage().contains(
					"Invalid path '" + path + "'"));
		}
		mgmt.close();
	}
}
