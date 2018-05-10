package com.yulong.websocket.client;

import java.io.IOException;
import java.util.Map;

import com.yulong.websocket.client.frames.DataFrame;

/**
 * A WebSocket representing a connection to the remote host/port.
 */
public interface WebSocket {

	/**
	 * Check whether the opening handshake is complete.
	 * 
	 * @return
	 */
	public boolean handshakeFinished();

	/**
	 * Add a listener to the WebSocket.
	 */
	public void addListener(WebSocketListener listener);

	/**
	 * Send text data.
	 * 
	 * @param textData
	 * @throws IOException
	 */
	public void send(String textData) throws IOException;

	/**
	 * Send a series of binary data.
	 * 
	 * @param binaryData
	 * @throws IOException
	 */
	public void send(byte[] binaryData) throws IOException;

	/**
	 * Send text data with fragmentation. Each fragment is with the size 'perSize'.
	 * 
	 * @param textData
	 * @param perSize
	 * @throws IOException
	 */
	public void send(String textData, int perSize) throws IOException;

	/**
	 * Send a data frame.
	 * 
	 * @param dataFrame
	 * @throws IOException
	 */
	public void send(DataFrame dataFrame) throws IOException;

	/**
	 * Send a data frame.
	 * 
	 * @param dataFrame
	 * @throws IOException
	 */
	public void send(DataFrame dataFrame, int chopSize) throws IOException;

	/**
	 * Send sendArbitrary binary data.
	 * 
	 * @param rawData
	 */
	public void sendArbitrary(byte[] rawData) throws IOException;

	/**
	 * Send a ping frame with UTF-8 text.
	 * 
	 * @param payloadData
	 * @throws IOException
	 */
	public void ping(String payloadData) throws IOException;

	/**
	 * Send a ping frame with binary data
	 * 
	 * @param payloadData
	 * @throws IOException
	 */
	public void ping(byte[] payloadData) throws IOException;

	/**
	 * Send a pong frame with UTF-8 text.
	 * 
	 * @param payloadData
	 * @throws IOException
	 */
	public void pong(String payloadData) throws IOException;

	/**
	 * Send a pong frame with binary data
	 * 
	 * @param payloadData
	 * @throws IOException
	 */
	public void pong(byte[] payloadData) throws IOException;

	/**
	 * Close the WebSocket connection with the give code and reason.
	 * 
	 * @param code
	 * @param reason
	 * @throws IOException
	 */
	public void close(int code, String reason) throws IOException;

	/**
	 * Close the WebSocket connection with the give code and reason.
	 * 
	 * @param code
	 * @param reason
	 * @throws IOException
	 */
	public void close(int code, byte[] reason) throws IOException;

	/**
	 * Close the WebSocket connection from client. If response close frame is not received in
	 * 60 seconds, the client will close it anyway.
	 * 
	 * @throws IOException
	 */
	public void close() throws IOException;

	/**
	 * Get the connection state.
	 * 
	 * @return WebSocketState
	 */
	public WebSocketState getState();

	/**
	 * Get the response status code.
	 * 
	 * @return int
	 */
	public int getResponsStatusCode();

	/**
	 * Get the response headers.
	 * 
	 * @return Map
	 */
	public Map<String, String> getResponseHeaders();

	/**
	 * A flag indicating that the connection has sent a close frame.
	 * 
	 * @return boolean
	 */
	public boolean hasSentCloseHandshake();

	/**
	 * Get the underlying socket.
	 * 
	 * @return
	 */
	//public Socket getUnderlyingSocket();

	/**
	 * Check if the connection is closed by server.
	 * 
	 * @return
	 */
	public boolean hasClosedFromServer();

}
