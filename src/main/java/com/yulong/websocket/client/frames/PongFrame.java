package com.yulong.websocket.client.frames;

import com.yulong.websocket.client.utils.Utils;

/**
 * A ping frame that wraps the DataFrame.
 */
public class PongFrame {

	private byte[] applicationData;
	private DataFrame dataFrame;

	/**
	 * To construct a pong frame from the give dataFrame.
	 * 
	 * @param dataFrame
	 */
	public PongFrame(DataFrame dataFrame) {
		this.dataFrame = dataFrame;
		applicationData = dataFrame.getApplicationData();
	}

	/**
	 * To construct a pong frame with the give applicationData.
	 * 
	 * @param applicationData
	 */
	public PongFrame(byte[] applicationData) {
		this.applicationData = applicationData;
		dataFrame = new DataFrame(true, 10, true, applicationData);
	}

	/**
	 * To return the application data of the pong frame.
	 * 
	 * @return
	 */
	public byte[] getApplicationData() {
		return applicationData;
	}

	/**
	 * To return the application data as UTF-8 text of the pong frame.
	 * 
	 * @return
	 */
	public String getTextData() {
		return Utils.bytes2String(applicationData);
	}

	/**
	 * To return the data frame wrapped in the pong frame.
	 * 
	 * @return
	 */
	public DataFrame getDataFrame() {
		return dataFrame;
	}

}
