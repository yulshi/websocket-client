package com.yulong.websocket.client.frames;

import com.yulong.websocket.client.utils.Utils;

/**
 * A ping frame that wraps DataFrame.
 */
public class PingFrame {

	private byte[] applicationData;
	private DataFrame dataFrame;

	/**
	 * To counstruct a ping frame from the given dataFrame.
	 * 
	 * @param dataFrame
	 */
	public PingFrame(DataFrame dataFrame) {
		this.dataFrame = dataFrame;
		applicationData = dataFrame.getApplicationData();
	}

	/**
	 * To construct a ping frame with the given applicationData.
	 * 
	 * @param applicationData
	 */
	public PingFrame(byte[] applicationData) {
		this.applicationData = applicationData;
		dataFrame = new DataFrame(true, 9, true, applicationData);
	}

	/**
	 * To return the application data of the ping frame.
	 * 
	 * @return
	 */
	public byte[] getApplicationData() {
		return applicationData;
	}

	/**
	 * To return the application data as UTF-8 text.
	 * 
	 * @return
	 */
	public String getTextData() {
		return Utils.bytes2String(applicationData);
	}

	/**
	 * To return the data frame wrapped in the ping frame.
	 * 
	 * @return
	 */
	public DataFrame getDataFrame() {
		return dataFrame;
	}

}
