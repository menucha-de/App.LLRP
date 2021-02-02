package havis.llrpservice.server.management;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

public class JMXManagement implements Management {

	private static MBeanServer mBeanServer;
	private static int instanceCount = 0;

	private List<String> registeredPaths = new ArrayList<>();

	@Override
	public synchronized void open() throws ManagementException {
		if (mBeanServer == null) {
			mBeanServer = java.lang.management.ManagementFactory
					.getPlatformMBeanServer();
		}
		instanceCount++;
	}

	@Override
	public synchronized void close() throws ManagementException {
		for (String registeredPath : new ArrayList<>(registeredPaths)) {
			unregister(registeredPath);
		}
		instanceCount--;
		if (instanceCount == 0) {
			mBeanServer = null;
		}
	}

	@Override
	public synchronized void register(String path, Object mbean)
			throws ManagementException {
		try {
			mBeanServer.registerMBean(mbean, getObjectName(path));
			registeredPaths.add(path);
		} catch (ManagementException e) {
			throw e;
		} catch (Exception e) {
			throw new ManagementException("Cannot register bean at '" + path
					+ "'", e);
		}
	}

	@Override
	public synchronized void unregister(String path) throws ManagementException {
		try {
			mBeanServer.unregisterMBean(getObjectName(path));
			registeredPaths.remove(path);
		} catch (ManagementException e) {
			throw e;
		} catch (Exception e) {
			throw new ManagementException("Cannot unregister bean at '" + path
					+ "'", e);
		}
	}

	/**
	 * Converts a path to an object name.
	 * 
	 * @param path
	 *            path separated with <code>/</code>
	 * @return The object name
	 * @throws ManagementException
	 * @throws MalformedObjectNameException
	 */
	private ObjectName getObjectName(String path) throws ManagementException,
			MalformedObjectNameException {
		if (path == null) {
			throw new ManagementException("Invalid path '" + null + "'");
		}
		Path p = Paths.get(path);
		int nameCount = p.getNameCount();
		if (nameCount < 2 || nameCount > 3) {
			throw new ManagementException("Invalid path '" + path + "'");
		}
		StringBuffer objName = new StringBuffer();
		for (int i = 0; i < nameCount; i++) {
			switch (i) {
			case 1:
				objName.append(":type=");
				break;
			case 2:
				objName.append(",name=");
				break;
			}
			String name = p.getName(i).toString();
			String quoted = ObjectName.quote(name);
			if (quoted.substring(1, quoted.length() - 1).equals(name)) {
				quoted = name;
			}
			objName.append(quoted);
		}
		return new ObjectName(objName.toString());
	}
}
