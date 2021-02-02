package havis.llrpservice.sbc.service;

import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.Verifications;

import org.testng.Assert;
import org.testng.annotations.Test;

public class ReflectionServiceFactoryTest {

	@Test
	public void getService(@Mocked final _ServiceClassTest serviceClass)
			throws Exception {
		new NonStrictExpectations() {
			{
			}
		};
		ReflectionServiceFactory<_ServiceClassTest> factory = new ReflectionServiceFactory<>(
				"havis.llrpservice.sbc.service._ServiceClassTest", "setAddress");
		// get 2 service instances
		final String host = "host";
		final int port1 = 3;
		int timeout = 1000;
		_ServiceClassTest service1 = factory.getService(host, port1, timeout);
		_ServiceClassTest service2 = factory.getService(host, port1, timeout);
		Assert.assertNotNull(service1);
		// the services are cached => the address is set once and the returned
		// instances are equal
		new Verifications() {
			{
				serviceClass.setAddress(host, port1);
				times = 1;
			}
		};
		Assert.assertEquals(service1, service2);

		// release the second instance and get it again
		factory.release(service2);
		_ServiceClassTest service2a = factory.getService(host, port1, timeout);
		Assert.assertNotNull(service2a);
		// the same instance is returned
		Assert.assertEquals(service2a, service2);

		// get a service instance for another port
		int port2 = 4;
		_ServiceClassTest service3 = factory.getService(host, port2, timeout);
		Assert.assertNotNull(service3);
		// a new service instance is returned
		Assert.assertNotEquals(service3, service2);

		// release the instance and get it again
		factory.release(service3);
		_ServiceClassTest service3a = factory.getService(host, port2, timeout);
		Assert.assertNotNull(service3a);
		// a new instance is returned because the cache has been cleared
		Assert.assertNotEquals(service3, service3a);
	}

	@Test
	public void getServiceError() throws Exception {
		ReflectionServiceFactory<_ServiceClassTest> factory = new ReflectionServiceFactory<>(
				"havis.llrpservice.sbc.service._ServiceClassTest", "xxx");
		String host = "host";
		int port = 3;
		int timeout = 1000;
		try {
			factory.getService(host, port, timeout);
			Assert.fail();
		} catch (ServiceFactoryException e) {
			Assert.assertTrue(e.getMessage().contains("Cannot create service"));
		}
	}
}
