package havis.llrpservice.server.service.fsm;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides informations of the device.
 * 
 */
public class Device {
	private static final Logger log = Logger.getLogger(Device.class.getName());

	/**
	 * Formats an address eg. "FE:54:00:58:DE:3A" with delimiter ":"
	 * 
	 * @param addr
	 * @param delimiter
	 * @return
	 */
	public String formatAddress(byte[] addr, String delimiter) {
		if (addr == null) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < addr.length; i++) {
			sb.append(String.format("%02X%s", addr[i], (i < addr.length - 1) ? delimiter : ""));
		}
		return sb.toString();
	}

	/**
	 * Gets the EUI-48 MAC address of the network device which is used for an
	 * client connection.
	 * 
	 * @param socket
	 *            the client socket of the connection
	 * @return
	 * @throws SocketException
	 */
	public byte[] getLocalMacAddress(Socket socket) throws SocketException {
		return getLocalMacAddress(socket.getLocalAddress().getHostAddress());
	}

	/**
	 * Gets the EUI-48 MAC address of the network device which is used for a
	 * client connection.
	 * 
	 * @param localHostAddress
	 *            the local IP address
	 * @return
	 * @throws SocketException
	 */
	public byte[] getLocalMacAddress(String localHostAddress) throws SocketException {
		for (Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces(); e
				.hasMoreElements();) {
			NetworkInterface netint = e.nextElement();
			for (Enumeration<InetAddress> en = netint.getInetAddresses(); en.hasMoreElements();) {
				InetAddress inetAddress = en.nextElement();
				if (inetAddress.getHostAddress().equals(localHostAddress)) {
					if (log.isLoggable(Level.INFO)) {
						log.log(Level.INFO,
								"Get MAC address for " + localHostAddress + " (network interface "
										+ netint.getName() + "): "
										+ formatAddress(netint.getHardwareAddress(), ":"));
					}
					return netint.getHardwareAddress();
				}
			}
		}
		return null;
	}

	/**
	 * Converts a EUI-48 MAC address received with eg.
	 * {@link #getLocalMacAddress(Socket)} to EUI-64 format with an 24 bit
	 * Organization Unique Identifier (OUI-24) and an extension identifier
	 * prefix 0xFFFE.
	 * 
	 * @param macAddress
	 * @return
	 * @throws InvalidMacAddressException
	 */
	public byte[] getLocalEUI64Address(byte[] macAddress) throws InvalidMacAddressException {
		if (macAddress == null) {
			return null;
		} else if (macAddress.length == 0) {
			return new byte[0];
		} else if (macAddress.length != 6) {
			throw new InvalidMacAddressException("Invalid length " + macAddress.length + "/6");
		}
		byte[] ret = new byte[8];
		for (int i = 0; i < macAddress.length; i++) {
			ret[i + (i < 3 ? 0 : 2)] = macAddress[i];
		}
		ret[3] = (byte) 0xFF;
		ret[4] = (byte) 0xFE;
		return ret;
	}

	/**
	 * Returns a system property as byte array. The property is interpreted as
	 * unsigned integer. If the property does not exist then an environment
	 * variable with the same name is used. The byte array is in big endian byte
	 * order. The last element of the byte array contains the least significant
	 * byte.
	 * 
	 * @param propertyName
	 * @return
	 */
	public void getIntIdentification(String propertyName, byte[] returnBytes)
			throws NumberFormatException {
		String propertyValue = null;
		if (propertyName != null && !propertyName.isEmpty()) {
			propertyValue = System.getProperty(propertyName);
			if (propertyValue != null) {
				propertyValue = propertyValue.trim();
				if (propertyValue.isEmpty()) {
					propertyValue = System.getenv(propertyName);
					if (propertyValue != null) {
						propertyValue = propertyValue.trim();
					}
				}
			}
		}
		if (propertyValue == null || propertyValue.isEmpty()) {
			for (int i = 0; i < returnBytes.length; i++) {
				returnBytes[i] = 0;
			}
			return;
		}
		byte[] propertyValueBytes = new BigInteger(propertyValue).toByteArray();
		int propertyValueBytesIndex = propertyValueBytes.length - 1;
		for (int i = returnBytes.length - 1; i >= 0; i--) {
			returnBytes[i] = propertyValueBytesIndex >= 0
					? propertyValueBytes[propertyValueBytesIndex] : 0;
			propertyValueBytesIndex--;
		}
	}
}
