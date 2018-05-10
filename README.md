# WebSocket Client Utility
This is a WebSocket client library written in Java NIO.

## Build and Install
```
mvn clean install
```
## Usage
```java
// Create a WebSocket client instance:
WebSocketClient client = new WebSocketClient("localhost", 8080, "/sample-chat/chat");

// Create a WebSocket instance:
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

} catch (IOException e) {
  e.printStackTrace();
} catch (InterruptedException e) {
  e.printStackTrace();
}
```
