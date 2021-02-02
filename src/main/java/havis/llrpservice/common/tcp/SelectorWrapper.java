package havis.llrpservice.common.tcp;

import java.io.IOException;
import java.nio.channels.Selector;

/**
 * This class wraps methods of {@link Selector}.
 * <p>
 * The functionality of these methods can be mocked with frameworks like JMockit
 * if this wrapper is used around the real implementation.
 * </p>
 */
class SelectorWrapper {

	private final Selector selector;

	SelectorWrapper(Selector selector) {
		this.selector = selector;
	}

	int select() throws IOException {
		return selector.select();
	}

	void close() throws IOException {
		selector.close();
	}
}
