package com.yulong.websocket.client;

/**
 * An excpeiton indicating that a protocol error occurs when
 * reading the data frame.
 */
public class ProtocolException extends Exception {

	public ProtocolException(String msg) {
		super(msg);
	}

	public ProtocolException(String msg, Throwable e) {
		super(msg, e);
	}

}
