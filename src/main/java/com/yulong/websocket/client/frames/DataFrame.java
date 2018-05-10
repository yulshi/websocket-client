package com.yulong.websocket.client.frames;

import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import com.yulong.websocket.client.ProtocolException;
import com.yulong.websocket.client.utils.Utils;

/**
 * A data frame that comforms to WebSocket protocol.
 */
public class DataFrame {

	private static final SimpleDateFormat sdf = new SimpleDateFormat("~~~~~~ HH:mm:ss ");
	private static final Random random = new Random(System.currentTimeMillis());

	// The parts composing the data frame:
	private boolean fin;
	private int rsv;
	private int opcode;
	private boolean mask;
	private byte[] maskKey = null;
	private byte[] extensionData = new byte[0];
	private byte[] applicationData = new byte[0];

	private byte[] rawData = null;

	// The calculated fields:
	private int payloadLength = -1;

	/**
	 * A convenient constructor to create a Text Data Frame without extensions.
	 * 
	 * @param fin
	 * @param opcode
	 * @param mask
	 * @param textData
	 */
	public DataFrame(boolean fin, int opcode, boolean mask, String textData) {
		this(fin, opcode, mask, Utils.string2Bytes(textData));
	}

	/**
	 * A convenient constructor to create a Binary Data Frame without extensions.
	 * 
	 * @param fin
	 * @param opcode
	 * @param mask
	 * @param applicationData
	 */
	public DataFrame(boolean fin, int opcode, boolean mask, byte[] applicationData) {
		this(fin, 0, opcode, mask, null, null, applicationData);
	}

	/**
	 * The constructor to create a Data Frame with all possible framing values. 
	 * 
	 * @param fin
	 * @param rsv
	 * @param opcode
	 * @param mask
	 * @param maskKey
	 * @param extData
	 * @param appData
	 */
	public DataFrame(boolean fin, int rsv, int opcode, boolean mask, byte[] maskKey, byte[] extData, byte[] appData) {
		this.fin = fin;
		this.rsv = rsv;
		this.opcode = opcode;
		this.mask = mask;
		if (maskKey != null) {
			this.maskKey = maskKey;
		} else {
			this.maskKey = new byte[4];
			random.nextBytes(this.maskKey);
		}
		if (extData != null) {
			this.extensionData = extData;
		}
		if (appData != null) {
			this.applicationData = appData;
		}
		this.payloadLength = this.extensionData.length + this.applicationData.length;
		createRawData();
	}

	/**
	 * Create a data frame from a received raw data.
	 * 
	 * @param rawData
	 */
	public DataFrame(byte[] rawData) {
		this.rawData = rawData;
		fromRawData();
	}

	/**
	 * Validate the frame data especially when getting from the server.
	 * 
	 * @throws ProtocolException
	 */
	public void validate() throws ProtocolException {

		if (rsv != 0) {
			throw new ProtocolException("The rsv value is not 0: " + rsv);
		}

		if ((opcode > 2 && opcode < 8) || opcode > 10) {
			throw new ProtocolException(String.format("The opcode[%s] is undefined.", opcode));
		}

		if (mask) {
			throw new ProtocolException("A server MUST NOT mask any frames that it sends to the client");
		}

		//    if (mask && (maskKey == null || maskKey.length != 4)) {
		//      throw new ProtocolException(String.format("The mask is set to true but maskKey[%s] is not correct", maskKey));
		//    } else if (!mask && (maskKey != null && maskKey.length > 0)) {
		//      throw new ProtocolException(String.format("The mask is set to false but maskKey[%s] is not correct", maskKey));
		//    }

		if (payloadLength == -1) {
			throw new ProtocolException("The payload length is not correct");
		}

	}

	/**
	 * Create the byte stream using the value of each part.
	 * 
	 * @return
	 */
	private void createRawData() {

		// Calculate the raw data length:
		int rawDataLength = 2; // The first byte must exist:
		if (mask) {
			rawDataLength += 4;
		}
		if (payloadLength > 125 && payloadLength <= 65535) {
			rawDataLength += 2;
		} else if (payloadLength > 65535) {
			rawDataLength += 8;
		}
		rawDataLength += payloadLength;

		ByteBuffer rawBuff = ByteBuffer.allocate(rawDataLength);

		// The first byte:
		byte firstByte = (byte) (opcode & 0x0f);
		firstByte = (byte) ((rsv << 4) | firstByte);
		firstByte = fin ? (byte) (firstByte | 0x80) : firstByte;
		rawBuff.put(firstByte);

		// The maks, payload length and extended payload length fields:
		if (payloadLength <= 125) {
			byte b = (byte) (payloadLength & 0xff);
			b = mask ? (byte) (b | 0x80) : b;
			rawBuff.put(b);
		} else if (payloadLength <= 65535) {
			byte b = (byte) (126 & 0xff);
			b = mask ? (byte) (b | 0x80) : b;
			rawBuff.put(b);
			rawBuff.putShort((short) payloadLength);
		} else {
			byte b = (byte) (127 & 0xff);
			b = mask ? (byte) (b | 0x80) : b;
			rawBuff.put(b);
			rawBuff.putLong(payloadLength);
		}

		// The mask key field:
		if (mask) {
			for (byte b : maskKey) {
				rawBuff.put(b);
			}
		}

		// The payload data field:
		byte[] payloadData = Utils.combine(extensionData, applicationData);
		if (mask) {
			for (int i = 0; i < payloadLength; i++) {
				rawBuff.put((byte) (payloadData[i] ^ maskKey[i % 4]));
			}
		} else {
			rawBuff.put(payloadData);
		}

		rawData = rawBuff.array();

	}

	/**
	 * The constructor to create a Data Frame according to the given data. 
	 * 
	 * @param data
	 */
	private void fromRawData() {

		System.out.println(sdf.format(new Date()) + "Received Dataframe: ["
				+ Utils.showPartOfTextIfTooLong(Utils.toHexString(rawData)) + "]");

		ByteBuffer rawDataBuff = ByteBuffer.wrap(rawData);

		// The first byte:
		byte b = rawDataBuff.get();
		fin = ((b & 0x80) == 0x80);
		rsv = (b & 0x70) >> 4;
		opcode = b & 0x0f;

		// Read the second byte:
		b = rawDataBuff.get();
		mask = ((b & 0x80) == 0x80);
		int payloadLengthTmp = (b & 0x7f);

		// Payload length:
		long length = -1;
		if (payloadLengthTmp <= 125) {
			length = payloadLengthTmp;
		} else if (payloadLengthTmp == 126) {
			byte[] bytes = new byte[4];
			bytes[2] = rawDataBuff.get();
			bytes[3] = rawDataBuff.get();
			ByteBuffer buff = ByteBuffer.wrap(bytes);
			length = buff.getInt();
		} else if (payloadLengthTmp == 127) {
			byte[] bytes = new byte[8];
			rawDataBuff.get(bytes);
			ByteBuffer buff = ByteBuffer.wrap(bytes);
			length = buff.getLong();
		}

		// Payload data:
		if (length < 0 || length > Integer.MAX_VALUE) {
			payloadLength = -1;
			//throw new ProtocolException("The payload length is not correct: " + dataLength);
		} else {
			payloadLength = (int) length;
			applicationData = new byte[payloadLength];
			if (mask) {
				maskKey = new byte[4];
				rawDataBuff.get(maskKey);
				for (int i = 0; i < applicationData.length; i++) {
					applicationData[i] = (byte) (rawDataBuff.get() ^ maskKey[i % 4]);
				}
			} else {
				maskKey = null;
				rawDataBuff.get(applicationData);
			}
		}

	}

	/*** The getter method for each part of the data frame ***/
	public boolean isFin() {
		return fin;
	}

	public int getRsv() {
		return rsv;
	}

	public boolean isMask() {
		return mask;
	}

	public int getOpcode() {
		return opcode;
	}

	public long getPayloadLength() {
		return payloadLength;
	}

	public byte[] getMaskKey() {
		return maskKey;
	}

	public byte[] getExtensionData() {
		return extensionData;
	}

	public byte[] getApplicationData() {
		return applicationData;
	}

	public byte[] getPayloadData() {
		return Utils.combine(extensionData, applicationData);
	}

	public String getTextMessage() {
		return Utils.bytes2String(applicationData);
	}

	public byte[] getRawData() {
		return rawData;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("[");
		sb.append("FIN=" + fin);
		sb.append(", ");
		sb.append("RSV=" + rsv);
		sb.append(", ");
		sb.append("OPCODE=" + opcode);
		sb.append(", ");
		sb.append("MASK=" + mask);
		sb.append(", ");
		sb.append("PAYLOADLENGTH=" + payloadLength);
		sb.append(", ");
		sb.append("MASKKEY=" + Utils.toHexString(maskKey));
		sb.append(", ");
		if (opcode == 8) {
			CloseFrame closeFrame = new CloseFrame(this);
			sb.append("CODE=" + closeFrame.getCode());
			sb.append(", ");
			sb.append("REASON(Txt)=" + Utils.showPartOfTextIfTooLong(Utils.bytes2String(closeFrame.getReason())));
			sb.append(", ");
			sb.append("REASON(Hex)=" + Utils.showPartOfTextIfTooLong(Utils.toHexString(closeFrame.getReason())));
		} else {
			sb.append("PAYLOAD=" + Utils.showPartOfTextIfTooLong(Utils.toHexString(applicationData)));
			sb.append(", ");
			sb.append("TEXT=" + Utils.showPartOfTextIfTooLong(Utils.bytes2String(applicationData)));
		}
		sb.append("]");
		return sb.toString();
	}

}
