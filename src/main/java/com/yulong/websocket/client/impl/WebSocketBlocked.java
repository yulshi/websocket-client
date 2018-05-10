package com.yulong.websocket.client.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;

import com.yulong.websocket.client.HandShakeException;
import com.yulong.websocket.client.ProtocolException;
import com.yulong.websocket.client.WebSocketListener;
import com.yulong.websocket.client.WebSocketState;
import com.yulong.websocket.client.frames.CloseFrame;
import com.yulong.websocket.client.frames.DataFrame;
import com.yulong.websocket.client.frames.PingFrame;
import com.yulong.websocket.client.frames.PongFrame;
import com.yulong.websocket.client.utils.Utils;
import com.yulong.websocket.client.utils.WebSocketKey;

/**
 * A blocked WebSocket connection to the remote host/port.
 */
public class WebSocketBlocked extends AbstractWebSocket implements Runnable {

	private Socket sock = null;
	private BufferedReader reader = null;
	private InputStream in = null;
	private OutputStream out = null;
	private StringBuilder receivedBytes = null;

	/**
	 * Try to open a WebSocket connection with the given version, sub protocols and extensions.
	 * 
	 * @param remoteHost
	 * @param remotePort
	 * @param wsPath
	 * @param wsVersion
	 * @param subProtocols
	 * @param extensions
	 * @return
	 */
	public WebSocketBlocked(String remoteHost, int remotePort, String wsPath, boolean secure, String wsVersion,
			String[] subProtocols, String[] extensions, Map<String, String[]> optionalHeaders) {

		connect(remoteHost, remotePort, wsPath, secure, wsVersion, subProtocols, extensions, optionalHeaders);
	}

	/**
	 * Read the WebSocket data frames from the inputstream of the socket.
	 */
	@Override
	public void run() {

		if (state == WebSocketState.OPEN) {

			try {
				while (true) {

					if (stopped) {
						break;
					}

					DataFrame dataFrame = readDataFrame(in);

					for (WebSocketListener listener : listeners) {
						listener.onDataFrame(dataFrame);
					}

					dataFrame.validate();

					int opcode = dataFrame.getOpcode();

					if (opcode == 1) {
						// textual message:
						String msg = readTextMessage(dataFrame, in);
						for (WebSocketListener listener : listeners) {
							listener.onMessage(msg);
						}
					} else if (opcode == 2) {
						// binary message:
						byte[] payload = readBinaryMessage(dataFrame, in);
						for (WebSocketListener listener : listeners) {
							listener.onMessage(payload);
						}
					} else if (opcode >= 8) {
						processControlFrame(dataFrame);
						if (opcode == 8) {
							break;
						}
					}
				}

			} catch (ProtocolException e) {
				log("Failed to read data frame from server due to " + e.getMessage());
				e.printStackTrace(System.out);
				for (WebSocketListener listener : listeners) {
					listener.onError(e);
				}
				disconnect();
			}

		}

	}

	/**
	 * Check if the connection is closed by server.
	 * 
	 * @return
	 */
	@Override
	public boolean hasClosedFromServer() {
		try {
			return in.available() == 0;
		} catch (IOException e) {
			log("failing to read due to: " + e);
			return true;
		}
	}

	/**
	 * Send the data to server.
	 * 
	 * @param data
	 */
	protected void sendData(byte[] data) throws IOException {
		log(">>" + Utils.showPartOfTextIfTooLong(Utils.toHexString(data)));
		out.write(data);
		out.flush();
	}

	/**
	 * Disconnect from server.
	 */
	protected void disconnect() {
		log("Closing socket ...");
		stopped = true;
		if (sock != null) {
			try {
				sock.close();
			} catch (IOException ex) {
				ex.printStackTrace(System.out);
			}
		}
		state = WebSocketState.CLOSED;
	}

	/******************** Opening handshake **************************/
	/**
	 * Connect to the remote host and initiate the opening handshake with the given WebSocket version,
	 * sub protocols and extensions.
	 * 
	 * @param wsVersion
	 * @param subProtocols
	 * @param extensions
	 */
	private void connect(String host, int port, String path, boolean secure, String wsVersion, String[] subProtocols,
			String[] extensions, Map<String, String[]> optionalHeaders) {

		String protocol = "ws";
		if (secure) {
			protocol = "wss";
		}
		log("Connecting to " + protocol + "://" + host + ":" + port + path);

		// Generate the value for Sec-WebSocket-Key header:
		WebSocketKey webSocketKey = new WebSocketKey();
		state = WebSocketState.CONNECTING;
		try {
			if (secure) {
				String wlHome = System.getProperty("WL_HOME");
				System.setProperty("javax.net.ssl.trustStore", wlHome + "/server/lib/cacerts");
				System.setProperty("javax.net.ssl.trustStorePassword", "changeit");
				SocketFactory sf = SSLSocketFactory.getDefault();
				sock = sf.createSocket(host, port);
			} else {
				sock = new Socket(host, port);
			}
			in = sock.getInputStream();
			reader = new BufferedReader(new InputStreamReader(in));
			out = sock.getOutputStream();

			// Start the openning hankshake from client:
			sendString(out, "GET " + path + " HTTP/1.1");
			sendString(out, "Host: " + host + ":" + port);
			sendString(out, "Upgrade: websocket");
			sendString(out, "Connection: Upgrade");
			sendString(out, "Origin: http://" + host + ":" + port);
			sendString(out, "Sec-WebSocket-Key: " + webSocketKey.getKey());
			if (wsVersion != null) {
				sendString(out, "Sec-WebSocket-Version: " + wsVersion);
			}
			if (subProtocols != null) {
				sendString(out, "Sec-WebSocket-Protocol: " + Utils.array2String(subProtocols));
			}
			if (extensions != null) {
				sendString(out, "Sec-WebSocket-Extensions: " + Utils.array2String(extensions));
			}
			if (optionalHeaders != null && !optionalHeaders.isEmpty()) {
				for (Map.Entry<String, String[]> optionalHeaderEntry : optionalHeaders.entrySet()) {
					sendString(out, optionalHeaderEntry.getKey() + ": " + Utils.array2String(optionalHeaderEntry.getValue()));
				}
			}
			sendString(out, "");
			out.flush();

			// Read the Client's openning handshake:
			new Thread(new Runnable() {

				@Override
				public void run() {
					String statusLine = null;
					String line = null;
					try {
						while ((line = reader.readLine()) != null) {

							if ("".equals(line)) {
								log("");
							} else {
								log("Received ... " + line);
							}

							if (line.trim().equals("")) {
								// End of reading headers:
								break;
							}

							// Get the status line:
							if (statusLine == null) {
								statusLine = line;
								continue;
							}

							// Get all the response headers:
							int index = line.indexOf(":");
							String headerName = line.substring(0, index);
							String headerValue = line.substring(index + 1).trim();
							responseHeaders.put(headerName.toLowerCase(), headerValue);

						}
					} catch (IOException e) {
						e.printStackTrace();
					}
					// Check the status line:
					if (statusLine != null) {
						String[] statusParts = statusLine.split(" ");
						responsStatusCode = Integer.parseInt(statusParts[1].trim());
					} else {
						responsStatusCode = -2;
					}
				}
			}).start();

			// Check the response code periodically:
			long start = System.currentTimeMillis();
			int waitingSeconds = 5;
			while ((System.currentTimeMillis() - start) / 1000 < waitingSeconds) {
				if (responsStatusCode > 0) {
					break;
				} else {
					Utils.sleepSecs(1);
				}
			}

			handshakeFinished = true;

			if (responsStatusCode < 0) {
				// The connection is not established, close it and reconnect:
				throw new HandShakeException("The connection is not established in 5 seconds");
			}

			if (responsStatusCode != 101) {

				log("The response status code is not 101: " + responsStatusCode);
				disconnect();

			} else {

				// Check the 'Upgrade' header:
				String upgradeHeader = responseHeaders.get("upgrade").toLowerCase();
				if (upgradeHeader == null || !upgradeHeader.equalsIgnoreCase("websocket")) {
					throw new HandShakeException(
							"Lack the Upgrade header or the header value is not websocket: " + upgradeHeader);
				}

				// Check the 'Connection' header:
				String connectionHeader = responseHeaders.get("connection").toLowerCase();
				if (connectionHeader == null || !connectionHeader.contains("upgrade")) {
					throw new HandShakeException(
							"Lack the Connection header or the header value does not contain 'Upgrade': " + connectionHeader);
				}

				// Check the 'Sec-WebSocket-Accept' header:
				String acceptHeader = responseHeaders.get("Sec-WebSocket-Accept".toLowerCase());
				if (acceptHeader == null || !acceptHeader.equals(webSocketKey.getAccept())) {
					throw new HandShakeException("Lack the Sec-WebSocket-Accept header or the header value is not "
							+ webSocketKey.getAccept() + ": " + acceptHeader);
				}

				state = WebSocketState.OPEN;
				instanceId = ++number;

			}

		} catch (HandShakeException e) {
			log(e.getMessage());
			disconnect();
		} catch (IOException e) {
			e.printStackTrace();
			disconnect();
		}

	}

	/******************** Read data from socket ***********************/
	/**
	 * Read a WebSocket text message which may be fragmented into several fragmentations.
	 * 
	 * @param dataFrame
	 * @param in
	 * @return
	 * @throws ProtocolException
	 */
	private String readTextMessage(DataFrame dataFrame, InputStream in) throws ProtocolException {
		StringBuilder sb = new StringBuilder(dataFrame.getTextMessage());
		if (!dataFrame.isFin() && dataFrame.getOpcode() == 1) {
			while (true) {
				DataFrame continueDf = readDataFrame(in);
				if (continueDf.getOpcode() == 0) {
					sb.append(continueDf.getTextMessage());
				} else if (continueDf.getOpcode() >= 8) {
					processControlFrame(continueDf);
				} else {
					throw new ProtocolException("Wrong data is contained within continuation data frame: " + continueDf);
				}
				if (continueDf.isFin() && continueDf.getOpcode() == 0) {
					break;
				}
			}
		}
		return sb.toString();
	}

	/**
	 * Read a WebSocket binary message which might be fragmented into several fragmentations.
	 * 
	 * @param dataFrame
	 * @param in
	 * @return
	 * @throws ProtocolException
	 */
	private byte[] readBinaryMessage(DataFrame dataFrame, InputStream in) throws ProtocolException {
		List<Byte> list = new ArrayList<Byte>();
		for (byte b : dataFrame.getApplicationData()) {
			list.add(b);
		}
		if (!dataFrame.isFin() && dataFrame.getOpcode() == 2) {
			while (true) {
				DataFrame continueDf = readDataFrame(in);
				if (continueDf.getOpcode() < 7) {
					for (byte b : continueDf.getApplicationData()) {
						list.add(b);
					}
				} else {
					processControlFrame(continueDf);
				}
				if (continueDf.isFin() && continueDf.getOpcode() == 0) {
					break;
				}
			}
		}
		byte[] bytes = new byte[list.size()];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = list.get(i).byteValue();
		}
		return bytes;
	}

	/**
	 * Process the control frames.
	 * 
	 * @param dataFrame
	 */
	private void processControlFrame(DataFrame dataFrame) {
		int opcode = dataFrame.getOpcode();
		if (opcode == 8) {
			CloseFrame closeFrame = new CloseFrame(dataFrame);
			hasReceivedCloseHandshake = true;
			for (WebSocketListener listener : listeners) {
				listener.onClose(closeFrame);
			}
		} else if (opcode == 9) {
			PingFrame pingFrame = new PingFrame(dataFrame);
			for (WebSocketListener listener : listeners) {
				listener.onPing(pingFrame);
			}
		} else if (opcode == 10) {
			PongFrame pongFrame = new PongFrame(dataFrame);
			for (WebSocketListener listener : listeners) {
				listener.onPong(pongFrame);
			}
		}
	}

	/**
	 * Read a data frame from the inputstream of the socket.
	 * 
	 * @param in
	 * @return
	 * @throws ProtocolException
	 */
	private DataFrame readDataFrame(InputStream in) throws ProtocolException {

		boolean mask = false;
		DataFrame dataFrame = null;

		ByteBuffer basicBuff = ByteBuffer.allocate(14);
		ByteBuffer rawDataBuff = null;
		receivedBytes = new StringBuilder();

		try {

			// Read the first byte:
			byte b = readByte(in);
			basicBuff.put(b);

			// Read the second byte:
			b = readByte(in);
			basicBuff.put(b);

			mask = ((b & 0x80) == 0x80);
			int payloadLength = (b & 0x7f);

			// Read extended payload length if exist:
			long dataLength = 0;
			if (payloadLength <= 125) {
				dataLength = payloadLength;
			} else if (payloadLength == 126) {
				byte[] bytes = new byte[] { readByte(in), readByte(in) };
				dataLength = Utils.toInt(bytes);
				basicBuff.put(bytes);
			} else if (payloadLength == 127) {
				byte[] bytes = new byte[8];
				for (int i = 0; i < 8; i++) {
					bytes[i] = readByte(in);
				}
				dataLength = Utils.toLong(bytes);
				basicBuff.put(bytes);
			}

			if (dataLength < 0 || dataLength > Integer.MAX_VALUE) {
				dataLength = 0;
			}

			int totalLength = basicBuff.position() + (mask ? 4 : 0) + (int) dataLength;
			rawDataBuff = ByteBuffer.allocate(totalLength);

			rawDataBuff.put(basicBuff.array(), 0, basicBuff.position());
			while (rawDataBuff.remaining() > 0) {
				rawDataBuff.put(readByte(in));
			}

		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		log("<<" + Utils.showPartOfTextIfTooLong(receivedBytes.toString()));
		if (rawDataBuff != null) {
			dataFrame = new DataFrame(rawDataBuff.array());
			log("<<" + dataFrame.toString());
		}

		return dataFrame;
	}

	/**
	 * Read one byte from the inputstream of the socket.
	 * 
	 * @param in
	 * @return
	 * @throws IOException
	 */
	private byte readByte(InputStream in) throws IOException {
		int i = in.read();
		if (i == -1) {
			throw new IOException("the channel has reached end-of-stream when reading data.");
		}
		byte b = (byte) i;
		receivedBytes.append((Utils.toHexString(b)));
		return b;
	}

	/***************************** common private method ***************************************/
	/**
	 * Send headers to server.
	 * 
	 * @param out
	 * @param s
	 * @throws IOException
	 */
	private void sendString(OutputStream out, String s) throws IOException {
		if (s.equals("")) {
			log("");
		} else {
			log("Sending  ... " + s);
		}
		String terminated = s + "\r\n";
		byte[] sBytes = terminated.getBytes("UTF-8");
		out.write(sBytes);
	}

}
