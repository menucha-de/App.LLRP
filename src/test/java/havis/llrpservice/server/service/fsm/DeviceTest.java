package havis.llrpservice.server.service.fsm;

import java.io.IOException;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.testng.Assert;
import org.testng.annotations.Test;

import test._EnvTest;

public class DeviceTest {
	@Test
	public void formatAddress() throws Exception {
		Device device = new Device();
		// FE:54:00:58:DE:3A
		byte[] addr = new byte[] { (byte) 0xFE, 0x54, 0, 0x58, (byte) 0xDE, 0x3A };
		Assert.assertEquals(device.formatAddress(addr, ""), "FE540058DE3A");
		addr = new byte[] { (byte) 0xFE, 0x54, 0 };
		Assert.assertEquals(device.formatAddress(addr, ":"), "FE:54:00");
	}

	@Test
	public void getLocalMacAddress() throws Exception {
		ExecutorService threadPool = Executors.newFixedThreadPool(1);
		Future<?> future = threadPool.submit(new Runnable() {

			@Override
			public void run() {
				try {
					ServerSocket ssocket = new ServerSocket(_EnvTest.SERVER_PORT_1);
					ssocket.accept();
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
					}
					ssocket.close();
				} catch (IOException e) {
					Assert.fail();
				}
			}
		});
		// wait for a started server
		Thread.sleep(500);
		// get first IP address with an assigned MAC address
		String addr = null;
		byte[] macAddr = null;
		Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
		for (NetworkInterface netint : Collections.list(networkInterfaces)) {
			if (netint.getHardwareAddress() != null
					&& netint.getInetAddresses().hasMoreElements()) {
				addr = netint.getInetAddresses().nextElement().getHostAddress();
				macAddr = netint.getHardwareAddress();
				break;
			}
		}
		// create a client socket with the IP address
		Socket socket = new Socket(addr, _EnvTest.SERVER_PORT_1);
		// get the MAC address
		Device device = new Device();
		byte[] macAddr2 = device.getLocalMacAddress(socket);
		Assert.assertEquals(macAddr2.length, 6);
		Assert.assertEquals(macAddr2, macAddr);
		socket.close();

		future.get();
		threadPool.shutdown();
	}

	@Test
	public void getLocalEUI64Address() throws Exception {
		Device device = new Device();
		// no MAC address
		Assert.assertNull(device.getLocalEUI64Address(null));
		// empty MAC address
		Assert.assertEquals(device.getLocalEUI64Address(new byte[0]).length, 0);
		// invalid MAC address
		try {
			device.getLocalEUI64Address(new byte[] { 1 });
			Assert.fail();
		} catch (InvalidMacAddressException e) {
			Assert.assertTrue(e.getMessage().contains("1/6"));
		} catch (Exception e) {
			Assert.fail();
		}
		// valid MAC address: FE:54:00:58:DE:3A
		byte[] macAddr = new byte[] { (byte) 0xFE, 0x54, 0, 0x58, (byte) 0xDE, 0x3A };
		byte[] eui64Addr = device.getLocalEUI64Address(macAddr);
		// FE:54:00:FF:FE:58:DE:3A
		Assert.assertEquals(eui64Addr, new byte[] { (byte) 0xFE, 0x54, 0, (byte) 0xFF, (byte) 0xFE,
				0x58, (byte) 0xDE, 0x3A });
	}

	@Test
	public void getIntIdentification() throws Exception {
		String propertyName = "mica.device.serial_no";
		Device device = new Device();
		System.setProperty(propertyName, "");
		byte[] value = new byte[3];
		device.getIntIdentification(propertyName, value);
		Assert.assertEquals(value, new byte[] { 0, 0, 0 });

		System.setProperty(propertyName, "1");
		value = new byte[1];
		device.getIntIdentification(propertyName, value);
		Assert.assertEquals(value, new byte[] { 1 });

		System.setProperty(propertyName, "255");
		value = new byte[1];
		device.getIntIdentification(propertyName, value);
		Assert.assertEquals(value, new byte[] { (byte) 0xFF });

		System.setProperty(propertyName, " 258 ");
		value = new byte[2];
		device.getIntIdentification(propertyName, value);
		Assert.assertEquals(value, new byte[] { 1, 2 });

		System.setProperty(propertyName, "258");
		value = new byte[1];
		device.getIntIdentification(propertyName, value);
		Assert.assertEquals(value, new byte[] { 2 });

		System.setProperty(propertyName, "a");
		try {
			device.getIntIdentification(propertyName, value);
			Assert.fail();
		} catch (NumberFormatException e) {
		}

		// remove system property
		System.setProperty(propertyName, "");
	}
}
