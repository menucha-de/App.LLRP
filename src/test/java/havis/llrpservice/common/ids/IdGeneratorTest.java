package havis.llrpservice.common.ids;

import org.testng.Assert;
import org.testng.annotations.Test;

public class IdGeneratorTest {

	@Test
	public void getLongId() {
		IdGenerator.resetLongId();
		Assert.assertEquals(IdGenerator.getNextLongId(), 1);
		Assert.assertEquals(IdGenerator.getNextLongId(), 2);
		Assert.assertEquals(IdGenerator.getNextLongId(), 3);
	}
}
