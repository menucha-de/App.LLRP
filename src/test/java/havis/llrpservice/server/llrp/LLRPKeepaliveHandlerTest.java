package havis.llrpservice.server.llrp;

import mockit.Delegate;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import mockit.Verifications;

import org.testng.annotations.Test;

public class LLRPKeepaliveHandlerTest {

	public enum Variant {
		EXCEPTION, REGULAR
	};

	@Test
	public void run(@Mocked final LLRPConnectionHandler connection)
			throws Exception {
		class TestVariant {
			Variant type;
		}

		final TestVariant variant = new TestVariant();
		new NonStrictExpectations() {
			{
				connection.requestSendingKeepaliveMessage();
				result = new Delegate<LLRPConnectionHandler>() {
					@SuppressWarnings("unused")
					public void requestSendingKeepalive() throws Exception {
						if (variant.type == Variant.EXCEPTION) {
							throw new Exception("huhu");
						}
					}
				};
			}

		};

		variant.type = Variant.REGULAR;
		long interval = 500;
		LLRPKeepaliveHandler keepalive = new LLRPKeepaliveHandler(connection,
				interval);
		keepalive.start();
		// wait for sending of first keepalive message
		Thread.sleep(700);

		new Verifications() {
			{
				connection.requestSendingKeepaliveMessage();
				times = 1;

				connection.getServerEventHandler();
				times = 0;
			}
		};

		// confirm the first keepalive message but not the second one
		// => a third message is not send because the thread has been stopped
		keepalive.setAcknowledged(true);
		Thread.sleep(3 * interval);

		new Verifications() {
			{
				connection.requestSendingKeepaliveMessage();
				times = 2;

				connection.getServerEventHandler().abortExecution(withInstanceOf(Exception.class));
				times = 1;
			}
		};

		variant.type = Variant.EXCEPTION;
		keepalive = new LLRPKeepaliveHandler(connection, interval);
		keepalive.start();
		Thread.sleep(1200);
		keepalive.stop(3000 /* unexpectedTimeout */);
		new Verifications() {
			{
				// a second message is not send because the thread has been stopped
				connection.requestSendingKeepaliveMessage();
				times = 3;
				
				connection.getServerEventHandler().abortExecution(withInstanceOf(Exception.class));
				times = 1;
			}
		};

	}
}
