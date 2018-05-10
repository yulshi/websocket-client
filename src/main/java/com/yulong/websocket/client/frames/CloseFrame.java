package com.yulong.websocket.client.frames;

import java.nio.ByteBuffer;
import java.util.Arrays;

import com.yulong.websocket.client.utils.Utils;

/**
 * A close frame that wraps DataFrame.
 */
public class CloseFrame {

	private int code = 1005;
	private byte[] reason = new byte[] {};
	private DataFrame dataFrame = null;

	/**
	 * To construct a close frame with code and reason.
	 * 
	 * @param code
	 * @param reason
	 */
	public CloseFrame(int code, byte[] reason) {
		this.code = code;
		this.reason = reason;
		generateDataFrame();
	}

	/**
	 * To construct a close frame from the dataFrame. 
	 * 
	 * @param dataFrame
	 */
	public CloseFrame(DataFrame dataFrame) {
		this.dataFrame = dataFrame;
		generateCodeAndReason();
	}

	/**
	 * To get the code.
	 * 
	 * @return
	 */
	public int getCode() {
		return code;
	}

	/**
	 * To get the reason.
	 * 
	 * @return
	 */
	public byte[] getReason() {
		return reason;
	}

	/**
	 * To get the data frame from the close frame.
	 * 
	 * @return
	 */
	public DataFrame getDataFrame() {
		return dataFrame;
	}

	/**
	 * To generate the code and reason using the given data frame.
	 */
	private void generateCodeAndReason() {

		byte[] applicationData = dataFrame.getApplicationData();

		if (applicationData == null || applicationData.length == 0) {
			code = 0;
		} else if (applicationData.length == 1) {
			code = applicationData[0] & 0xff;
		} else {
			code = Utils.toInt(new byte[] { applicationData[0], applicationData[1] });
			// get the reason:
			if (applicationData.length > 2) {
				reason = Arrays.copyOfRange(applicationData, 2, applicationData.length);
			}
		}

	}

	/**
	 * To generate the data frame using code and reason.
	 */
	private void generateDataFrame() {

		byte[] applicationData = reason;
		if (code >= 0) {
			ByteBuffer buff = ByteBuffer.allocate(2);
			buff.putShort((short) code);
			applicationData = Utils.combine(buff.array(), reason);
		}

		dataFrame = new DataFrame(true, 8, true, applicationData);

	}

}
