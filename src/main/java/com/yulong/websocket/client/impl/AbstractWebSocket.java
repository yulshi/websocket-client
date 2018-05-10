package com.yulong.websocket.client.impl;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.yulong.websocket.client.WebSocket;
import com.yulong.websocket.client.WebSocketListener;
import com.yulong.websocket.client.WebSocketState;
import com.yulong.websocket.client.frames.CloseFrame;
import com.yulong.websocket.client.frames.DataFrame;
import com.yulong.websocket.client.frames.PingFrame;
import com.yulong.websocket.client.frames.PongFrame;
import com.yulong.websocket.client.utils.Utils;

public abstract class AbstractWebSocket implements WebSocket {

  private static final SimpleDateFormat sdf = new SimpleDateFormat("~~~~~~ HH:mm:ss ");
  protected static int number = 0;
  protected int instanceId = number;

  // The connection state:
  protected int responsStatusCode = -1;
  protected Map<String, String> responseHeaders = new HashMap<String, String>();
  protected boolean hasSentCloseHandshake = false;
  protected boolean hasReceivedCloseHandshake = false;
  protected boolean handshakeFinished = false;

  protected WebSocketState state = WebSocketState.CLOSED;
  protected boolean stopped = false;
  protected List<WebSocketListener> listeners = new LinkedList<WebSocketListener>();

  /**
   * Add a listener to the WebSocket.
   */
  @Override
  public void addListener(WebSocketListener listener) {
    listeners.add(listener);
  }

  /**
   * Indicate that the opening handshake is complete.
   */
  @Override
  public boolean handshakeFinished() {
    return handshakeFinished;
  }

  /**
   * Send text data.
   * 
   * @param textData
   * @throws IOException
   */
  @Override
  public void send(String textData) throws IOException {
    send(textData, -1);
  }

  /**
   * Send text data with fragmentation. Each fragment is with the size 'perSize'.
   * 
   * @param textData
   * @param perSize
   * @throws IOException
   */
  @Override
  public void send(String textData, int perSize) throws IOException {

    List<String> splits = Utils.split(textData, perSize);
    int size = splits.size();

    if (size == 1) {
      DataFrame dataFrame = new DataFrame(true, 1, true, textData);
      send(dataFrame);
    } else {
      DataFrame dataFrame1 = new DataFrame(false, 1, true, splits.get(0));
      send(dataFrame1);
      for (int i = 1; i < size - 1; i++) {
        DataFrame dataFrame2 = new DataFrame(false, 0, true, splits.get(i));
        send(dataFrame2);
      }
      DataFrame dataFrame3 = new DataFrame(true, 0, true, splits.get(size - 1));
      send(dataFrame3);
    }

  }

  /**
   * Send a series of binary data.
   * 
   * @param binaryData
   * @throws IOException
   */
  public void send(byte[] binaryData) throws IOException {
    DataFrame dataFrame = new DataFrame(true, 2, true, binaryData);
    send(dataFrame);
  }

  /**
   * Send a data frame.
   * 
   * @param dataFrame
   * @throws IOException
   */
  @Override
  public void send(DataFrame dataFrame) throws IOException {
    send(dataFrame, -1);
  }

  /**
   * Send a data frame in chops of chopSize octets.
   * 
   * @param dataFrame
   * @throws IOException
   */
  @Override
  public void send(DataFrame dataFrame, int chopSize) throws IOException {
    log(">>" + dataFrame);
    byte[] rawData = dataFrame.getRawData();
    if (chopSize == -1 || chopSize >= rawData.length) {
      sendData(rawData);
    } else {
      for (int i = 0; i < rawData.length;) {
        int sentLengh = chopSize;
        if (rawData.length - i < chopSize) {
          sentLengh = rawData.length - i;
        }
        byte[] chop = new byte[sentLengh];
        System.arraycopy(rawData, i, chop, 0, chop.length);
        sendData(chop);
        i += sentLengh;
      }
    }
  }

  /**
   * Send sendArbitrary binary data.
   * 
   * @param rawData
   */
  @Override
  public void sendArbitrary(byte[] rawData) throws IOException {
    sendData(rawData);
  }

  /**
   * Send a ping frame with UTF-8 text.
   * 
   * @param payloadData
   * @throws IOException
   */
  @Override
  public void ping(String payloadData) throws IOException {
    byte[] data = null;
    if (payloadData != null) {
      data = Utils.string2Bytes(payloadData);
    }
    ping(data);
  }

  /**
   * Send a ping frame with binary data
   * 
   * @param payloadData
   * @throws IOException
   */
  public void ping(byte[] payloadData) throws IOException {
    PingFrame pingFrame = new PingFrame(payloadData);
    send(pingFrame.getDataFrame());
  }

  /**
   * Send a pong frame with UTF-8 text.
   * 
   * @param payloadData
   * @throws IOException
   */
  public void pong(String payloadData) throws IOException {
    byte[] data = null;
    if (payloadData != null) {
      data = Utils.string2Bytes(payloadData);
    }
    pong(data);
  }

  /**
   * Send a pong frame with binary data
   * 
   * @param payloadData
   * @throws IOException
   */
  public void pong(byte[] payloadData) throws IOException {
    PongFrame pongFrame = new PongFrame(payloadData);
    send(pongFrame.getDataFrame());
  }

  /**
   * Close the WebSocket connection with the give code and reason.
   * 
   * @param code
   * @param reason
   * @throws IOException
   */
  @Override
  public void close(int code, String reason) throws IOException {
    close(code, Utils.string2Bytes(reason));
  }

  /**
   * Close the WebSocket connection with the give code and reason.
   * 
   * @param code
   * @param reason
   * @throws IOException
   */
  public void close(int code, byte[] reason) throws IOException {
    CloseFrame responseCloseWraper = new CloseFrame(code, reason);
    DataFrame dataFrame = responseCloseWraper.getDataFrame();
    send(dataFrame);
    hasSentCloseHandshake = true;
  }

  /**
   * Close the WebSocket connection from client. If response close frame is not received in
   * 60 seconds, the client will close it anyway.
   * 
   * @throws IOException
   */
  @Override
  public void close() throws IOException {
    close(1000, "");
    Utils.sleepSecs(2);
    if (!hasReceivedCloseHandshake) {
      disconnect();
    }
  }

  /**
   * Get the connection state.
   * 
   * @return
   */
  @Override
  public WebSocketState getState() {
    return state;
  }

  /**
   * Get the response status code.
   * 
   * @return
   */
  @Override
  public int getResponsStatusCode() {
    return responsStatusCode;
  }

  /**
   * Get the response headers.
   * 
   * @return
   */
  @Override
  public Map<String, String> getResponseHeaders() {
    return responseHeaders;
  }

  /**
   * A flag indicating that the connection has sent a close frame.
   * 
   * @return
   */
  @Override
  public boolean hasSentCloseHandshake() {
    return hasSentCloseHandshake;
  }

  /**
   * Utility log.
   * 
   * @param msg
   */
  protected void log(String msg) {
    System.out.println(sdf.format(new Date()) + "WebSocket[" + instanceId + "]:" + msg);
  }

  protected abstract void sendData(byte[] data) throws IOException;

  protected abstract void disconnect();

}
