package com.yulong.websocket.client;

import com.yulong.websocket.client.frames.CloseFrame;
import com.yulong.websocket.client.frames.DataFrame;
import com.yulong.websocket.client.frames.PingFrame;
import com.yulong.websocket.client.frames.PongFrame;

/**
 * 
 * The WebSocket listener methods that are invoked when data frames
 * arrive.
 */
public interface WebSocketListener {

	/**
	 * A listener method this is invoked when a data frame arrives.
	 */
	public void onDataFrame(DataFrame dataFrame);

	/**
	 * A listener method that is invoked when text message arrives.
	 */
	public void onMessage(String msg);

	/**
	 * A listener method that is invoked when binary message arrives.
	 */
	public void onMessage(byte[] binaryData);

	/**
	 * A listener method that is invoked when a close frame arrives.
	 */
	public void onClose(CloseFrame closeFrame);

	/**
	 * A listener method that is invoked when a Ping frame arrives.
	 */
	public void onPing(PingFrame pingFrame);

	/**
	 * A listener method that is invoked when a Pong frame arrives.
	 */
	public void onPong(PongFrame pongFrame);

	/**
	 * A listener method that is invoked when error occurs.
	 */
	public void onError(ProtocolException e);

}
