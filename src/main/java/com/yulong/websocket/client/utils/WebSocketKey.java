package com.yulong.websocket.client.utils;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Random;

/**
 * WebSocket key that is used during openning handshake.
 */
public class WebSocketKey {

	public final static String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
	private static final Random random = new Random(System.currentTimeMillis());

	private String key = null;
	private String accept = null;

	/**
	 * To generate a WebSocket key that is available for others.
	 */
	public WebSocketKey() {
		key = genKey();
		accept = genAccepted();
	}

	/**
	 * To return the generated key as a string.
	 * 
	 * @return
	 */
	public String getKey() {
		return key;
	}

	/**
	 * To return the calculated accept as a string.
	 * 
	 * @return
	 */
	public String getAccept() {
		return accept;
	}

	/**
	 * To generate the accept based on the key.
	 * 
	 * @return
	 */
	private String genAccepted() {
		String accept = encode(getSha1Hash());
		//String accept = "xcMaQU2LUKISZZrI/Lj2ZgoGbZM=";
		return accept;
	}

	/**
	 * To generate a random key.
	 * 
	 * @return
	 */
	private String genKey() {
		//return "wqSdZUp5iYE/5OR7krU4LQ==";
		byte[] bytes = new byte[16];
		random.nextBytes(bytes);
		return encode(bytes);
	}

	/**
	 * To get the SHA1 hash code.
	 * 
	 * @return
	 */
	private byte[] getSha1Hash() {
		String concatenatedString = key + GUID;
		byte[] result = null;
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("SHA-1");
			md.update(concatenatedString.getBytes("iso-8859-1"), 0, concatenatedString.length());
			result = md.digest();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return result;
	}

	/**
	 * To encode the given byte array into a base-64 encoded string.
	 * 
	 * @param bytes
	 * @return
	 */
	private String encode(byte[] bytes) {
		return Utils.bytes2String(Base64.getEncoder().encode(bytes), "iso-8859-1");
	}

	public static void main(String[] args) {
		WebSocketKey webSocketKey = new WebSocketKey();
		System.out.println(webSocketKey.getKey());
		System.out.println(webSocketKey.getAccept());
	}

}
