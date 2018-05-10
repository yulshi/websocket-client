package com.yulong.websocket.client;

import java.util.HashMap;
import java.util.Map;

import com.yulong.websocket.client.impl.WebSocketBlocked;
import com.yulong.websocket.client.impl.WebSocketNio;

/**
 * A factory that creates a WebSocket instance.
 */
public final class WebSocketFactory {

	private boolean blockMode = false;
	private boolean secure = false;
	private static Map<Boolean, WebSocketFactory> instances = new HashMap<Boolean, WebSocketFactory>();

	private WebSocketFactory(boolean secure) {
		this.secure = secure;
		this.blockMode = Boolean.getBoolean("ws.io.mode.block");
	}

	public synchronized static WebSocketFactory getInstance(boolean secure) {
		WebSocketFactory instance = instances.get(Boolean.valueOf(secure));
		if (instance == null) {
			instance = new WebSocketFactory(secure);
			instances.put(Boolean.valueOf(secure), instance);
		}
		return instance;
	}

	/**
	 * Open a WebSocket connection.
	 * 
	 * @param remoteHost
	 * @param remotePort
	 * @param wsPath
	 * @param version
	 * @param subProtocols
	 * @param extensions
	 * @return
	 */
	public WebSocket openWebSocket(String remoteHost, int remotePort, String wsPath, String version,
			String[] subProtocols, String[] extensions, Map<String, String[]> optionalHeaders) {
		if (version == null) {
			version = "13";
		}

		if (blockMode || secure) {
			// Block mode:
			WebSocketBlocked webSocket = new WebSocketBlocked(remoteHost, remotePort, wsPath, secure, version, subProtocols,
					extensions, optionalHeaders);
			if (webSocket.getState() == WebSocketState.OPEN) {
				Thread t = new Thread(webSocket);
				t.setDaemon(true);
				t.start();
			}
			return webSocket;
		} else {
			// Non-block mode:
			WebSocketNio webSocket = new WebSocketNio(remoteHost, remotePort, wsPath, secure, version, subProtocols,
					extensions, optionalHeaders);
			Thread t = new Thread(webSocket);
			t.setDaemon(true);
			t.start();
			waitHandshake(webSocket);
			return webSocket;
		}
	}

	/**
	 * Wait until the handshake is complete.
	 * 
	 * @param webSocket
	 */
	private void waitHandshake(WebSocket webSocket) {
		long start = System.currentTimeMillis();
		try {
			while (!webSocket.handshakeFinished()) {
				Thread.sleep(500);
				if ((System.currentTimeMillis() - start) / 1000 > 5) {
					break;
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

}
