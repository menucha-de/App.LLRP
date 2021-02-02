package havis.llrpservice.server.service;

import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.nio.file.Path;

import org.testng.annotations.Test;

import havis.llrpservice.common.io.PathHandler;
import havis.llrpservice.common.io.XMLFile;
import havis.llrpservice.xml.properties.LLRPServerInstancePropertiesType;

public class PropertiesValidatorTest {

	private static final Path BASE_PATH = new PathHandler()
			.toAbsolutePath("havis/llrpservice/server/service/propertiesValidator");
	private static final Path INIT_PATH = BASE_PATH.resolve("LLRPServerInstanceProperties.xml");

	@Test
	public void validateServerInstanceConfiguration() throws Exception {
		// load server instance configuration
		LLRPServerInstancePropertiesType conf = new XMLFile<>(
				LLRPServerInstancePropertiesType.class, INIT_PATH, null /* latestPath */)
						.getContent();
		PropertiesValidator pv = new PropertiesValidator();
		pv.validate((LLRPServerInstancePropertiesType) null /* props */, null /* filePath */);
		// validate the valid properties
		pv.validate(conf, INIT_PATH.toString());

		String validValue = conf.getInstanceId();
		String[] invalidInstanceIds = { "", "defaults+" };
		for (String invalidInstanceId : invalidInstanceIds) {
			conf.setInstanceId(invalidInstanceId);
			validate(conf, INIT_PATH.toString(),
					"LLRPServerInstanceProperties.xml:/LLRPServerInstanceProperties/instanceId");
		}
		conf.setInstanceId(validValue);
	}

	private void validate(LLRPServerInstancePropertiesType props, String filePath,
			String errorMessageEnd) {
		try {
			new PropertiesValidator().validate(props, filePath);
			fail();
		} catch (PropertiesException e) {
			assertTrue(e.getMessage().endsWith(errorMessageEnd));
		}
	}
}
