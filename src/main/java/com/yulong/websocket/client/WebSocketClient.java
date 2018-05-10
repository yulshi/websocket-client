package com.yulong.websocket.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.yulong.websocket.client.frames.CloseFrame;
import com.yulong.websocket.client.frames.DataFrame;
import com.yulong.websocket.client.frames.PingFrame;
import com.yulong.websocket.client.frames.PongFrame;
import com.yulong.websocket.client.utils.Utils;

/**
 * A WebSocket client that can help users send and receive WebSocket messages.
 */
public class WebSocketClient implements WebSocketListener {

	// Listener related fields:
	private DataFrame listenerDataFrame = null;
	private PongFrame listenerPong = null;
	private PingFrame listenerPing = null;
	private CloseFrame listenerClose = null;
	private String listenerTextMessage = null;
	private byte[] listenerBinaryMessage = null;

	private List<DataFrame> listenerDataFrameList = new ArrayList<DataFrame>();
	private List<PongFrame> listenerPongList = new ArrayList<PongFrame>();
	private List<PingFrame> listenerPingList = new ArrayList<PingFrame>();
	private List<String> listenerTextMessageList = new ArrayList<String>();
	private List<byte[]> listenerBinaryMessageList = new ArrayList<byte[]>();

	private WebSocket webSocket = null;
	private String clientName = null;
	private boolean sentBackCloseOnReceiving = true;

	/**
	 * To create a WebSocket client with given host, port and path.
	 * 
	 * @param host 
	 * @param port 
	 * @param path
	 */
	public WebSocketClient(String host, int port, String path) {
		this(host, port, path, false, null, null, null, null);
	}

	/**
	 * To create a WebSocket client with given host, port and path.
	 * 
	 * @param host 
	 * @param port 
	 * @param path
	 */
	public WebSocketClient(String host, int port, String path, boolean secure) {
		this(host, port, path, secure, null, null, null, null);
	}

	/**
	 * To create a WebSocket client with host, port, path, WebSocket version, WebSocket sub protocols and 
	 * WebSocket extensions.
	 * 
	 * @param host
	 * @param port
	 * @param path
	 * @param version
	 * @param subProtocols
	 * @param extensions
	 */
	public WebSocketClient(String host, int port, String path, boolean secure, String version, String[] subProtocols,
			String[] extensions, Map<String, String[]> optionalHeaders) {
		webSocket = WebSocketFactory.getInstance(secure).openWebSocket(host, port, path, version, subProtocols, extensions,
				optionalHeaders);
		webSocket.addListener(this);
	}

	/**
	 * To get the WebSocket that is created on this client.
	 * 
	 * @return
	 */
	public WebSocket getWebSocket() {
		return webSocket;
	}

	public void setName(String clientName) {
		this.clientName = clientName;
	}

	public String getName() {
		if (clientName != null) {
			return clientName;
		} else {
			return "Standalone_Client";
		}
	}

	public void setSentBackCloseOnReceiving(boolean sentBackCloseOnReceiving) {
		this.sentBackCloseOnReceiving = sentBackCloseOnReceiving;
	}

	/**
	 * A listener method this is invoked when a data frame arrives.
	 */
	public void onDataFrame(DataFrame dataFrame) {
		listenerDataFrame = dataFrame;
		listenerDataFrameList.add(dataFrame);
	}

	/**
	 * A listener method that is invoked when text message arrives.
	 */
	@Override
	public void onMessage(String msg) {
		log("[Text Message Received] " + Utils.showPartOfTextIfTooLong(msg));
		listenerTextMessage = msg;
		listenerTextMessageList.add(msg);
	}

	/**
	 * A listener method that is invoked when binary message arrives.
	 */
	@Override
	public void onMessage(byte[] binaryData) {
		log("[Binary Message Received] " + Utils.showPartOfTextIfTooLong(Utils.toHexString(binaryData)));
		listenerBinaryMessage = binaryData;
		listenerBinaryMessageList.add(binaryData);
	}

	/**
	 * A listener method that is invoked when close frame arrives.
	 * Send a response close frame with the same data if not yet. 
	 */
	@Override
	public void onClose(CloseFrame closeFrame) {

		listenerClose = closeFrame;
		int code = closeFrame.getCode();
		String reason = Utils.bytes2String(closeFrame.getReason());
		log("[Close Received] closed due to code=[" + code + "] and reason=[" + Utils.showPartOfTextIfTooLong(reason)
				+ "]");

		// Send a close if not yet:
		if (sentBackCloseOnReceiving) {
			if (!webSocket.hasSentCloseHandshake()) {
				log("Sending closing handshake as a result of receiving server closing handshake");
				try {
					webSocket.close(code, reason);
				} catch (IOException e) {
					log("Failed to send back close frame due to: " + e);
				}
			}
		}

	}

	/**
	 * A listener method that is invoked when ping frame arrives.
	 * Send pong frame to server if not yet.
	 */
	@Override
	public void onPing(PingFrame pingFrame) {
		listenerPing = pingFrame;
		listenerPingList.add(pingFrame);
		byte[] applicationData = pingFrame.getApplicationData();
		PongFrame pongFrame = new PongFrame(applicationData);
		try {
			webSocket.send(pongFrame.getDataFrame());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * A listener method that is invoked when pong frame arrives.
	 */
	@Override
	public void onPong(PongFrame pongFrame) {
		listenerPong = pongFrame;
		listenerPongList.add(pongFrame);
		String pongData = Utils.bytes2String(pongFrame.getApplicationData());
		log("[Pong Received] " + Utils.showPartOfTextIfTooLong(pongData));
	}

	/**
	 * A listener method that is invoked when an error occurs.
	 * Send a close frame with the code 1002 and the reason exception message.
	 */
	@Override
	public void onError(ProtocolException e) {
		log("[Error Received] " + e.getMessage());
		try {
			webSocket.close(1002, e.getMessage());
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	/********************* Getter Methods *********************/
	public DataFrame getListenerDataFrame() {
		return listenerDataFrame;
	}

	public List<DataFrame> getListenerDataFrameList() {
		return listenerDataFrameList;
	}

	public String getListenerTextMessage() {
		return listenerTextMessage;
	}

	public List<String> getListenerTextMessageList() {
		return listenerTextMessageList;
	}

	public byte[] getListenerBinaryMessage() {
		return listenerBinaryMessage;
	}

	public List<byte[]> getListenerBinaryMessageList() {
		return listenerBinaryMessageList;
	}

	public PongFrame getListenerPong() {
		return listenerPong;
	}

	public List<PongFrame> getListenerPongList() {
		return listenerPongList;
	}

	public PingFrame getListenerPing() {
		return listenerPing;
	}

	public List<PingFrame> getListenerPingList() {
		return listenerPingList;
	}

	public CloseFrame getListenerClose() {
		return listenerClose;
	}

	private void log(String msg) {
		System.out.println("[" + getName() + "] " + msg);
	}

	/**
	 * A test.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {

		WebSocketClient client = new WebSocketClient("localhost", 8080, "/sample-chat/chat");
		client.setSentBackCloseOnReceiving(true);
		WebSocket webSocket = client.getWebSocket();

		if (webSocket.getState() != WebSocketState.OPEN) {
			System.out.println("The websocket connection is not established.");
			return;
		}

		try {

			webSocket.send("ctmsgccc:hello");
			Thread.sleep(1000);
			webSocket.send("ctmsgddd:hello");
			Thread.sleep(1000);

			//      webSocket.send(new DataFrame(true, 1, true, "Hello"), 3);
			//      Thread.sleep(1000);
			//
			//      webSocket.send("This message has been splited into serval parts", 10);
			//      Thread.sleep(2000);
			//
			//      StringBuilder sb = new StringBuilder();
			//      for (int i = 0; i < 65535; i++) {
			//        sb.append("a");
			//      }
			//      webSocket.send(sb.toString());
			//      Thread.sleep(2000);
			//
			//      webSocket.send("quit");
			//      Thread.sleep(1000);
			//
			//      client.log("Closed: " + webSocket.hasClosedFromServer());

		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

	}

}
