package havis.llrpservice.server.service.messageHandling;

import havis.device.io.Direction;
import havis.device.io.IOConfiguration;
import havis.device.io.State;
import havis.llrpservice.data.message.parameter.GPIPortCurrentState;
import havis.llrpservice.data.message.parameter.GPOWriteData;

/**
 * Converts data structures from LLRP messages to data structures for the GPIO
 * controller interface.
 */
public class GPIOMessageConverter {
	/**
	 * Converts LLRP GPI data to an IO configuration for the GPIO interface.
	 * 
	 * @param state
	 * @return The converted configuration
	 */
	public IOConfiguration convert(GPIPortCurrentState state) {
		State pinState;
		switch (state.getState()) {
		case HIGH:
			pinState = State.HIGH;
			break;
		case LOW:
			pinState = State.LOW;
			break;
		case UNKNOWN:
		default:
			pinState = State.UNKNOWN;
		}
		return new IOConfiguration((short) state.getGpiPortNum(), Direction.INPUT, pinState,
				state.getGpiConfig());
	}

	/**
	 * Converts LLRP GPO data to an IO configuration for the GPIO interface.
	 * 
	 * @param gpoData
	 * @return The converted configuration
	 */
	public IOConfiguration convert(GPOWriteData gpoData) {
		return new IOConfiguration((short) gpoData.getGpoPortNum(), Direction.OUTPUT,
				gpoData.getGpoState() ? State.HIGH : State.LOW, false /* gpiEventsEnabled */);
	}
}
