package com.yulong.websocket.client.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
 * A non-blocked WebSocket connection to the remote host/port.
 */
public class WebSocketNio extends AbstractWebSocket implements Runnable {

  // The variables regarding remote host and websocket path:
  private String remoteHost = null;
  private int remotePort;
  private String wsPath = null;
  private boolean secure = false;
  private String version = "13";
  private String[] subProtocols = null;
  private String[] extensions = null;
  private Map<String, String[]> optionalHeaders;
  private WebSocketKey webSocketKey = null;

  // The socket:
  private SocketChannel socketChannel = null;
  private Selector selector;
  private ByteBuffer readerBuff = ByteBuffer.allocate(8192);
  private DataFrameBuilder dataFrameBuilder = new DataFrameBuilder();
  private boolean closedByServer = false;

  private List<DataFrame> textDataFrameList = new LinkedList<DataFrame>();
  private List<DataFrame> binaryDataFrameList = new LinkedList<DataFrame>();

  /**
   * The constructor to create a WebSocket.
   * 
   * @param hostname
   * @param port
   * @param path
   * @param version
   * @param subProtocols
   * @param extensions
   */
  public WebSocketNio(String hostname,
                      int port,
                      String path,
                      boolean secure,
                      String version,
                      String[] subProtocols,
                      String[] extensions,
                      Map<String, String[]> optionalHeaders) {
    this.remoteHost = hostname;
    this.remotePort = port;
    this.wsPath = path;
    this.secure = secure;
    this.version = version;
    this.subProtocols = subProtocols;
    this.extensions = extensions;
    this.optionalHeaders = optionalHeaders;
    try {
      selector = Selector.open();
    } catch (IOException e) {
      e.printStackTrace();
    }
    instanceId = ++number;
    initConnection();
  }

  /**
   * Start to connect and read data.
   */
  @Override
  public void run() {

    while (true) {

      if (stopped) {
        break;
      }

      try {

        //        for (SelectionKey key : selector.keys()) {
        //          System.out.println("selecting ... " + key.interestOps() + ":" + key.readyOps());
        //        }

        selector.select();

        //        System.out.println("-->" + selector.selectedKeys().size());

        Iterator<SelectionKey> iterSelectedKey = selector.selectedKeys().iterator();
        while (iterSelectedKey.hasNext()) {

          SelectionKey key = iterSelectedKey.next();
          iterSelectedKey.remove();

          if (!key.isValid()) {
            continue;
          }

          if (key.isConnectable()) {
            finishConnection(key);
          } else if (key.isReadable()) {
            if (state == WebSocketState.CLOSED) {
              stopped = true;
            }
            read(key);
          } else if (key.isWritable()) {
            if (state == WebSocketState.CONNECTING) {
              startOpeningHandshake(key);
            } else {
              log("can not read as connection is in the state " + state);
            }
          }

        }

      } catch (IOException e) {
        log("Error occurs due to " + e);
        e.printStackTrace();
        disconnect(null);
      } catch (HandShakeException e) {
        log("Handshake failed due to " + e);
        e.printStackTrace(System.out);
        disconnect(null);
        break;
      } catch (ProtocolException e) {
        log("Error occurs when reading data due to " + e);
        e.printStackTrace();
        for (WebSocketListener listener : listeners) {
          listener.onError(e);
        }
        disconnect(null);
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
    return closedByServer;
  }

  /**
   * Send data to server.
   * 
   * @param data 
   */
  protected void sendData(byte[] data) throws IOException {
    log(">>" + Utils.showPartOfTextIfTooLong(Utils.toHexString(data)));
    ByteBuffer buff = ByteBuffer.wrap(data);
    socketChannel.write(buff);
    long start = System.currentTimeMillis();
    while (buff.hasRemaining()) {
      log("remaining is " + buff.remaining() + ", will continue to send ...");
      socketChannel.write(buff);
      long end = System.currentTimeMillis();
      if ((end - start) / 1000 > 5) {
        break;
      }
    }
  }

  /**
   * Disconnect from server.
   */
  protected void disconnect() {
    disconnect(null);
  }

  /**************** connecting to remote host *******************/
  /**
   * Step 1: initialize the connection.
   */
  private void initConnection() {
    log("Connecting to ws://" + remoteHost + ":" + remotePort);
    SocketAddress remote = new InetSocketAddress(remoteHost, remotePort);
    SelectionKey key = null;
    try {
      if (!secure) {
        socketChannel = SocketChannel.open();
      } else {
        //TODO how to implement something like SSLSocketChannel?
        throw new IOException("There is not implementation in NIO for SSL channel.");
      }
      socketChannel.configureBlocking(false);
      socketChannel.connect(remote);
      key = socketChannel.register(selector, SelectionKey.OP_CONNECT);
      selector.wakeup();
    } catch (IOException e) {
      log("Failed to initiate the connection due to " + e);
      e.printStackTrace();
      if (key != null) {
        key.cancel();
      }
      if (socketChannel != null) {
        try {
          socketChannel.close();
        } catch (IOException e1) {
        }
      }
    }
  }

  /**
   * Step 2: finish the connection.
   * 
   * @param key
   */
  private void finishConnection(SelectionKey key) {
    SocketChannel socketChannel = (SocketChannel) key.channel();
    try {
      socketChannel.finishConnect();
      key.interestOps(SelectionKey.OP_WRITE);
      state = WebSocketState.CONNECTING;
    } catch (IOException e) {
      e.printStackTrace(System.out);
      key.cancel();
      state = WebSocketState.CLOSED;
    }
  }

  /**
   * Step 3: start the opening handshake.
   * 
   * @param key
   */
  private void startOpeningHandshake(SelectionKey key) {

    log("Starting openning handshake to " + wsPath);
    SocketChannel socketChannel = (SocketChannel) key.channel();

    webSocketKey = new WebSocketKey();

    StringBuilder sb = new StringBuilder();
    sb.append("GET " + wsPath + " HTTP/1.1\r\n");
    sb.append("Host: " + remoteHost + ":" + remotePort + "\r\n");
    sb.append("Upgrade: websocket\r\n");
    sb.append("Connection: Upgrade\r\n");
    sb.append("Origin: http://" + remoteHost + ":" + remotePort + "\r\n");
    sb.append("Sec-WebSocket-Key: " + webSocketKey.getKey() + "\r\n");
    if (version != null) {
      sb.append("Sec-WebSocket-Version: " + version + "\r\n");
    }
    if (subProtocols != null) {
      sb.append("Sec-WebSocket-Protocol: " + Utils.array2String(subProtocols) + "\r\n");
    }
    if (extensions != null) {
      sb.append("Sec-WebSocket-Extensions: " + Utils.array2String(extensions) + "\r\n");
    }
    if (optionalHeaders != null && !optionalHeaders.isEmpty()) {
      for (Map.Entry<String, String[]> optionalHeaderEntry : optionalHeaders.entrySet()) {
        sb.append(optionalHeaderEntry.getKey() + ": " + Utils.array2String(optionalHeaderEntry.getValue()) + "\r\n");
      }
    }

    sb.append("\r\n");

    String request = sb.toString();
    log("Sending ... \r\n" + request);
    ByteBuffer buff = ByteBuffer.wrap(Utils.string2Bytes(request));
    try {
      socketChannel.write(buff);
      key.interestOps(SelectionKey.OP_READ);
    } catch (IOException e) {
      log("Failed to send opening handshake due to " + e);
      e.printStackTrace(System.out);
      disconnect(key);
    }

  }

  /**
   * Step 4: read data from the WebSocket connection.
   * 
   * @param key
   * @throws HandShakeException 
   * @throws ProtocolException 
   */
  private void read(SelectionKey key) throws HandShakeException, ProtocolException {

    SocketChannel socketChannel = (SocketChannel) key.channel();

    readerBuff.clear();
    int numRead = 0;

    try {
      numRead = socketChannel.read(readerBuff);
    } catch (IOException e) {
      closedByServer = true;
      log("failing to read due to: " + e);
      disconnect(key);
      return;
    }

    if (numRead == -1) {
      closedByServer = true;
      log("the channel has reached end-of-stream when reading data.");
      disconnect(key);
      return;
    }

    byte[] data = new byte[numRead];
    System.arraycopy(readerBuff.array(), 0, data, 0, numRead);

    // Process the data according to the state:
    if (state == WebSocketState.CONNECTING) {
      if (proceedOpeningHandshake(data)) {
        state = WebSocketState.OPEN;
        key.interestOps(SelectionKey.OP_READ);
      } else {
        state = WebSocketState.CLOSED;
      }
    } else if (state == WebSocketState.OPEN) {
      log("<<" + Utils.showPartOfTextIfTooLong(Utils.toHexString(data)));
      proceedMessage(data);
    }

  }

  /**
   * Process the response data.
   * 
   * @param data
   * @return
   * @throws HandShakeException
   */
  private boolean proceedOpeningHandshake(byte[] data) throws HandShakeException {

    String responseAsText = Utils.bytes2String(data);
    log("receiving ... \r\n" + responseAsText);

    // Read the response of the opening handshake:
    List<String> list = new LinkedList<String>();
    BufferedReader reader = new BufferedReader(new StringReader(responseAsText));
    String line = null;
    try {
      while ((line = reader.readLine()) != null) {
        list.add(line);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    // If we have not got the response status code, we asume that the first line is the status line:
    if (responsStatusCode == -1) {
      try {
        String statusLine = list.remove(0);
        String[] statusParts = statusLine.split(" ");
        responsStatusCode = Integer.parseInt(statusParts[1].trim());
      } catch (Exception e) {
        throw new HandShakeException("Can not get the response status line due to " + e);
      }
    }

    for (String headerLine : list) {
      if (headerLine.trim().length() == 0) {
        // End of response headers:
        return proccedHandshake();
      }
      int index = headerLine.indexOf(":");
      String headerName = headerLine.substring(0, index);
      String headerValue = headerLine.substring(index + 1).trim();
      responseHeaders.put(headerName.toLowerCase(), headerValue);

    }

    return false;

  }

  /**
   * Check the response headers.
   * 
   * @return
   * @throws HandShakeException
   */
  private boolean proccedHandshake() throws HandShakeException {

    handshakeFinished = true;
    boolean handshakeSucceed = false;

    if (responsStatusCode == 101) {

      // Check the 'Upgrade' header:
      String upgradeHeader = responseHeaders.get("upgrade").toLowerCase();
      if (upgradeHeader == null || !upgradeHeader.equalsIgnoreCase("websocket")) {
        throw new HandShakeException("Lack the Upgrade header or the header value is not websocket: " + upgradeHeader);
      }

      // Check the 'Connection' header:
      String connectionHeader = responseHeaders.get("connection").toLowerCase();
      if (connectionHeader == null || !connectionHeader.contains("upgrade")) {
        throw new HandShakeException("Lack the Connection header or the header value does not contain 'Upgrade': "
            + connectionHeader);
      }

      // Check the 'Sec-WebSocket-Accept' header:
      String acceptHeader = responseHeaders.get("Sec-WebSocket-Accept".toLowerCase());
      if (acceptHeader == null || !acceptHeader.equals(webSocketKey.getAccept())) {
        throw new HandShakeException("Lack the Sec-WebSocket-Accept header or the header value is not "
            + webSocketKey.getAccept() + ": " + acceptHeader);
      }
      handshakeSucceed = true;

    }

    return handshakeSucceed;

  }

  /**
   * process the data received from WebSocket connection after handshake.
   * 
   * @param data
   * @throws ProtocolException 
   */
  private void proceedMessage(byte[] data) throws ProtocolException {

    //log("<<~~~~~~ " + Utils.toHexString(data));
    ByteBuffer dataBuff = ByteBuffer.wrap(data);
    DataFrame dataFrame = dataFrameBuilder.build(dataBuff);

    if (dataFrame != null) {

      log("<<" + dataFrame);

      for (WebSocketListener listener : listeners) {
        listener.onDataFrame(dataFrame);
      }

      dataFrame.validate();

      dataFrameBuilder = new DataFrameBuilder();

      boolean isFinal = dataFrame.isFin();
      int opcode = dataFrame.getOpcode();

      if (opcode == 0x08) {
        // If close frame:
        state = WebSocketState.CLOSING;
        CloseFrame closeFrame = new CloseFrame(dataFrame);
        hasReceivedCloseHandshake = true;
        for (WebSocketListener listener : listeners) {
          listener.onClose(closeFrame);
        }
      } else if (opcode == 0x09) {
        // If ping frame:
        PingFrame pingFrame = new PingFrame(dataFrame);
        for (WebSocketListener listener : listeners) {
          listener.onPing(pingFrame);
        }
      } else if (opcode == 0x0A) {
        // If pong frame:
        PongFrame pongFrame = new PongFrame(dataFrame);
        for (WebSocketListener listener : listeners) {
          listener.onPong(pongFrame);
        }
      } else if (opcode <= 0x02) {
        // Text frame:
        if (opcode == 0x01) {
          textDataFrameList.add(dataFrame);
        } else if (opcode == 0x02) {
          binaryDataFrameList.add(dataFrame);
        } else {
          if (textDataFrameList.size() != 0) {
            textDataFrameList.add(dataFrame);
          } else if (binaryDataFrameList.size() != 0) {
            binaryDataFrameList.add(dataFrame);
          }
        }
        if (isFinal) {
          if (textDataFrameList.size() != 0) {
            processTextMessage();
          } else if (binaryDataFrameList.size() != 0) {
            processBinaryMessage();
          }
        }
      } else {
        // Reserved opcodes:

      }

      // If there are remaining in dataBuff, continue to process the remaining data:
      if (dataBuff.hasRemaining()) {
        int remainingCount = dataBuff.remaining();
        log("Will continue to process the remaining data: " + remainingCount);
        byte[] remaining = new byte[remainingCount];
        dataBuff.get(remaining);
        proceedMessage(remaining);
      }

    }

  }

  /**
   * Compose the text messages:
   */
  private void processTextMessage() {
    StringBuilder sb = new StringBuilder();
    for (DataFrame dataFrame : textDataFrameList) {
      sb.append(dataFrame.getTextMessage());
    }
    textDataFrameList.clear();
    for (WebSocketListener listener : listeners) {
      listener.onMessage(sb.toString());
    }
  }

  /**
   * Compose the binary messages:
   */
  private void processBinaryMessage() {
    byte[] composed = new byte[0];
    for (DataFrame dataFrame : binaryDataFrameList) {
      composed = Utils.combine(composed, dataFrame.getApplicationData());
    }
    binaryDataFrameList.clear();
    for (WebSocketListener listener : listeners) {
      listener.onMessage(composed);
    }
  }

  /**
   * Disconnect the WebSocket connection.
   * 
   * @param key
   */
  private void disconnect(SelectionKey key) {
    log("Disconnecting from client side");
    state = WebSocketState.CLOSED;
    stopped = true;
    if (key != null) {
      key.cancel();
      try {
        key.channel().close();
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else {
      if (socketChannel != null) {
        try {
          socketChannel.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  /**
   * A private class to compose a data frame with the received data.
   * 
   * @author @author Yulong Shi(yu.long.shi@oracle.com)
   * @since 4/20/2012 12c
   *
   */
  private class DataFrameBuilder {

    private boolean mask = false;
    private int payloadLength = -1;

    ByteBuffer basicBuff = ByteBuffer.allocate(14);
    ByteBuffer rawDataBuff = null;
    int rawDataLength = -1;

    public DataFrame build(ByteBuffer dataBuff) {

      DataFrame dataFrame = null;

      while (dataBuff.hasRemaining()) {

        byte b = dataBuff.get();
        //log("processing byte: " + Utils.toHexString(b));

        if (basicBuff.position() == 0) {
          basicBuff.put(b);
          continue;
        }

        if (basicBuff.position() == 1) {
          basicBuff.put(b);
          mask = ((b & 0x80) == 0x80);
          payloadLength = (b & 0x7f);
          if (payloadLength <= 125) {
            basicBuff.limit(2);
          } else if (payloadLength == 126) {
            basicBuff.limit(2 + 2);
          } else if (payloadLength == 127) {
            basicBuff.limit(2 + 8);
          }
          if (mask) {
            basicBuff.limit(basicBuff.limit() + 4);
          }
          if (payloadLength == 0 && !mask) {
            byte[] rawData = new byte[2];
            basicBuff.clear();
            basicBuff.get(rawData);
            return new DataFrame(rawData);
          }
          continue;
        }

        if (basicBuff.remaining() > 0) {
          basicBuff.put(b);
        }
        if (basicBuff.remaining() == 0) {
          if (rawDataLength == -1) {
            // calculate the data length that needs to be read:
            long dataLength = payloadLength;
            if (payloadLength == 126) {
              dataLength = Utils.toInt(basicBuff, 2, 2);
            } else if (payloadLength == 127) {
              dataLength = Utils.toLong(basicBuff, 2, 8);
            }
            if (dataLength < 0 || dataLength > Integer.MAX_VALUE) {
              dataLength = 0;
            }
            rawDataLength = basicBuff.limit() + (int) dataLength;
            rawDataBuff = ByteBuffer.allocate(rawDataLength);
            basicBuff.position(0);
            rawDataBuff.put(basicBuff);
            basicBuff.position(basicBuff.limit());
            if (dataLength != 0 && basicBuff.limit() > 2) {
              // in this case, as we have read 'b' in basicBuff and we may need to read it again,
              // set the new position to 'position-1':
              rawDataBuff.position(rawDataBuff.position() - 1);
            }
          }
          if (rawDataBuff.remaining() > 0) {
            rawDataBuff.put(b);
          }
          if (rawDataBuff.remaining() == 0) {
            dataFrame = new DataFrame(rawDataBuff.array());
            break;
          }
        }
      }

      return dataFrame;

    }
  }

  public static void main(String[] args) {

    WebSocketNio webSocket = new WebSocketNio("localhost", 7001, "/chat", false, "13", null, null, null);
    byte[] data1 = Utils.fromHexString("8100");
    byte[] data2 = Utils.fromHexString("880203e8888276c58224");
    byte[] data = Utils.combine(data1, data2);
    try {
      webSocket.proceedMessage(data);
      webSocket.proceedMessage(Utils.fromHexString("752d"));
    } catch (ProtocolException e) {
      e.printStackTrace();
    }

  }

}
