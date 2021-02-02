package havis.llrpservice.server.llrp;

import havis.llrpservice.data.message.parameter.ReaderEventNotificationData;
import havis.util.platform.Platform;

import java.lang.management.ManagementFactory;

import mockit.Delegate;
import mockit.Mocked;
import mockit.NonStrictExpectations;

import org.testng.Assert;
import org.testng.annotations.Test;

public class LLRPMessageCreatorTest {

	@Test
	public void createReaderEventNotification(
			@Mocked final Platform systemController) throws Exception {
		class Data {
			boolean hasUTCClock = true;
			long uptime = 3;
		}
		final Data data = new Data();
		new NonStrictExpectations() {
			{
				systemController.hasUTCClock();
				result = new Delegate<Platform>() {
					@SuppressWarnings("unused")
					boolean hasUTCClock() {
						return data.hasUTCClock;
					}
				};

				systemController.getUptime();
				result = new Delegate<Platform>() {
					@SuppressWarnings("unused")
					long getUptime() {
						return data.uptime;
					}
				};
			}
		};
		LLRPMessageCreator creator = new LLRPMessageCreator();

		// with hardware clock and system uptime
		// the hardware clock is used
		long start = System.currentTimeMillis();
		ReaderEventNotificationData msg = creator
				.createReaderEventNotificationData(systemController);
		long end = System.currentTimeMillis();
		long micros = msg.getUtcTimestamp().getMicroseconds().longValue();
		Assert.assertTrue(micros >= start * 1000 && micros <= end * 1000);
		Assert.assertNull(msg.getUptime());

		// without hardware clock but with system uptime
		// the system uptime is used
		data.hasUTCClock = false;
		msg = creator.createReaderEventNotificationData(systemController);
		Assert.assertNull(msg.getUtcTimestamp());
		Assert.assertEquals(msg.getUptime().getMicroseconds().longValue(), 3000);

		// without hardware clock and without system uptime
		// the uptime of the JVM is used
		data.uptime = -1;
		start = ManagementFactory.getRuntimeMXBean().getUptime();
		msg = creator.createReaderEventNotificationData(systemController);
		end = ManagementFactory.getRuntimeMXBean().getUptime();
		micros = msg.getUptime().getMicroseconds().longValue();
		Assert.assertNull(msg.getUtcTimestamp());
		Assert.assertTrue(micros >= start * 1000 && micros <= end * 1000);
	}
}
