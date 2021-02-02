package havis.llrpservice.server.llrp;

import java.math.BigInteger;

import havis.llrpservice.data.message.parameter.ReaderEventNotificationData;
import havis.llrpservice.data.message.parameter.TLVParameterHeader;
import havis.llrpservice.data.message.parameter.UTCTimestamp;
import havis.llrpservice.data.message.parameter.Uptime;
import havis.llrpservice.server.platform.TimeStamp;
import havis.util.platform.Platform;
import havis.util.platform.PlatformException;

public class LLRPMessageCreator {

	public ReaderEventNotificationData createReaderEventNotificationData(Platform platform)
			throws PlatformException {
		TimeStamp ts = new TimeStamp(platform);
		if (ts.isUtc()) {
			return new ReaderEventNotificationData(new TLVParameterHeader((byte) 0),
					new UTCTimestamp(new TLVParameterHeader((byte) 0),
							new BigInteger(String.valueOf(ts.getTimestamp() * 1000))));
		}
		return new ReaderEventNotificationData(new TLVParameterHeader((byte) 0),
				new Uptime(new TLVParameterHeader((byte) 0),
						new BigInteger(String.valueOf(ts.getTimestamp() * 1000))));
	}
}
