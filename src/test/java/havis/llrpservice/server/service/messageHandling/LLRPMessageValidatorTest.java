package havis.llrpservice.server.service.messageHandling;

import havis.llrpservice.data.message.AddAccessSpec;
import havis.llrpservice.data.message.AddROSpec;
import havis.llrpservice.data.message.Message;
import havis.llrpservice.data.message.MessageHeader;
import havis.llrpservice.data.message.ProtocolVersion;
import havis.llrpservice.data.message.parameter.Custom;
import havis.llrpservice.data.message.parameter.LLRPStatus;
import havis.llrpservice.data.message.parameter.LLRPStatusCode;
import havis.llrpservice.data.message.parameter.ROSpecCurrentState;

import java.util.ArrayList;
import java.util.List;

import mockit.Delegate;
import mockit.Mocked;
import mockit.NonStrictExpectations;

import org.testng.Assert;
import org.testng.annotations.Test;

public class LLRPMessageValidatorTest {

	@Test
	public void validateProtocolVersion(@Mocked final Message message) {
		class Data {
			ProtocolVersion version;
		}
		final Data data = new Data();
		new NonStrictExpectations() {
			{
				message.getMessageHeader().getVersion();
				result = new Delegate<MessageHeader>() {
					@SuppressWarnings("unused")
					public ProtocolVersion getVersion() {
						return data.version;
					}
				};
			}
		};
		LLRPMessageValidator validator = new LLRPMessageValidator();

		// success (same protocol version)
		data.version = ProtocolVersion.LLRP_V1_1;
		LLRPStatus status = validator.validateProtocolVersion(message,
				ProtocolVersion.LLRP_V1_1);
		Assert.assertEquals(status.getStatusCode(), LLRPStatusCode.M_SUCCESS);

		// M_UNSUPPORTED_VERSION (different protocol version)
		data.version = ProtocolVersion.LLRP_V1_0_1;
		status = validator.validateProtocolVersion(message,
				ProtocolVersion.LLRP_V1_1);
		Assert.assertEquals(status.getStatusCode(),
				LLRPStatusCode.M_UNSUPPORTED_VERSION);
		Assert.assertTrue(status.getErrorDescription().contains(
				"Invalid protocol version"));

		data.version = ProtocolVersion.LLRP_V1_1;
		status = validator.validateProtocolVersion(message,
				ProtocolVersion.LLRP_V1_0_1);
		Assert.assertEquals(status.getStatusCode(),
				LLRPStatusCode.M_UNSUPPORTED_VERSION);
		Assert.assertTrue(status.getErrorDescription().contains(
				"Invalid protocol version"));
	}

	@Test
	public void validateSupportedVersion(@Mocked final Message message) {
		class Data {
			ProtocolVersion version;
		}
		final Data data = new Data();
		new NonStrictExpectations() {
			{
				message.getMessageHeader().getVersion();
				result = new Delegate<MessageHeader>() {
					@SuppressWarnings("unused")
					public ProtocolVersion getVersion() {
						return data.version;
					}
				};
			}
		};
		LLRPMessageValidator validator = new LLRPMessageValidator();

		// success (same protocol version)
		data.version = ProtocolVersion.LLRP_V1_1;
		LLRPStatus status = validator.validateSupportedVersion(message,
				ProtocolVersion.LLRP_V1_1 /* supportedVersion */);
		Assert.assertEquals(status.getStatusCode(), LLRPStatusCode.M_SUCCESS);

		// success (older protocol version)
		data.version = ProtocolVersion.LLRP_V1_0_1;
		status = validator.validateSupportedVersion(message,
				ProtocolVersion.LLRP_V1_1 /* supportedVersion */);
		Assert.assertEquals(status.getStatusCode(), LLRPStatusCode.M_SUCCESS);

		// M_UNSUPPORTED_VERSION (newer protocol version)
		data.version = ProtocolVersion.LLRP_V1_1;
		status = validator.validateSupportedVersion(message,
				ProtocolVersion.LLRP_V1_0_1 /* supportedVersion */);
		Assert.assertEquals(status.getStatusCode(),
				LLRPStatusCode.M_UNSUPPORTED_VERSION);
		Assert.assertTrue(status.getErrorDescription()
				.contains("not supported"));
	}

	@Test
	public void validateCustomExtension(@Mocked final Custom customParameter) {
		LLRPMessageValidator validator = new LLRPMessageValidator();

		// valid custom parameters
		List<Custom> customParameters = new ArrayList<>();
		LLRPStatus status = validator.validateCustomExtension(customParameters);
		Assert.assertEquals(status.getStatusCode(), LLRPStatusCode.M_SUCCESS);

		// invalid custom parameters
		customParameters.add(customParameter);
		status = validator.validateCustomExtension(customParameters);
		Assert.assertEquals(status.getStatusCode(),
				LLRPStatusCode.P_UNEXPECTED_PARAMETER);
	}

	@Test
	public void validateSpecStateAddROSpecROSpecCurrentState(
			@Mocked final AddROSpec spec) {
		new NonStrictExpectations() {
			{
				spec.getRoSpec().getCurrentState();
				result = ROSpecCurrentState.DISABLED;
			}
		};
		LLRPMessageValidator validator = new LLRPMessageValidator();

		// valid state
		LLRPStatus status = validator.validateSpecState(spec,
				ROSpecCurrentState.DISABLED);
		Assert.assertEquals(status.getStatusCode(), LLRPStatusCode.M_SUCCESS);

		// invalid state
		status = validator.validateSpecState(spec, ROSpecCurrentState.ACTIVE);
		Assert.assertEquals(status.getStatusCode(),
				LLRPStatusCode.M_FIELD_ERROR);
		Assert.assertTrue(status.getErrorDescription().contains(
				"Invalid ROSpec state"));
	}

	@Test
	public void validateSpecStateAddAccessSpecboolean(
			@Mocked final AddAccessSpec spec) {
		new NonStrictExpectations() {
			{
				spec.getAccessSpec().isCurrentState();
				result = true;
			}
		};
		LLRPMessageValidator validator = new LLRPMessageValidator();

		// valid state
		LLRPStatus status = validator
				.validateSpecState(spec, true /* active */);
		Assert.assertEquals(status.getStatusCode(), LLRPStatusCode.M_SUCCESS);

		// invalid state
		status = validator.validateSpecState(spec, false /* active */);
		Assert.assertEquals(status.getStatusCode(),
				LLRPStatusCode.M_FIELD_ERROR);
		Assert.assertTrue(status.getErrorDescription().contains(
				"Invalid AccessSpec state"));
	}
}
