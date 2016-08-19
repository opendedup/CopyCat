package com.datish.copycat;
/**
 * Copyright 2016 Datish Systems LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.java_websocket.client.DefaultSSLWebSocketClientFactory;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datish.copycat.events.VolumeEvent;
import com.datish.copycat.util.EasyX509TrustManager;
import com.google.common.eventbus.EventBus;


public class WebSocketUploadListener extends WebSocketClient {
	private boolean closed = true;
	private static EventBus eventUploadBus = new EventBus();
	Logger logger = LoggerFactory.getLogger(WebSocketUploadListener.class);
	public static void registerEvents(Object obj) {
		eventUploadBus.register(obj);
	}
	
	public WebSocketUploadListener(String server, int port, String password)
			throws UnsupportedEncodingException, URISyntaxException, NoSuchAlgorithmException, KeyManagementException, KeyStoreException, InterruptedException {
		super(new URI("wss://" + server + ":" + port
				+ "/uploadsocket?password="
				+ URLEncoder.encode(password, "UTF-8")), new Draft_17());
		SSLContext sslContext = null;
		sslContext = SSLContext.getInstance( "TLS" );
		sslContext.init( null, new TrustManager[] {new EasyX509TrustManager(null)}, null );
		// sslContext.init( null, null, null ); // will use java's default key and trust store which is sufficient unless you deal with self-signed certificates
		this.setWebSocketFactory( new DefaultSSLWebSocketClientFactory(sslContext));
		// TODO Auto-generated constructor stub
	}
	
	public void connect(String server, int port) throws InterruptedException {
		this.connectBlocking();
		logger.debug("WebSocket Initiated to {}:{}", server, port);
	}

	@Override
	public void onClose(int arg0, String arg1, boolean arg2) {
		this.closed = true;
		logger.debug("connection closed to  " + this.getURI().getRawPath());
	}

	@Override
	public void onError(Exception e) {
		logger.error("unable to process message", e);

	}

	@Override
	public void onMessage(String msg) {
		try {
			logger.debug("recieved {}",msg);
			eventUploadBus.post(new VolumeEvent(msg));
		}catch(Exception e) {
			logger.error("unable to process " + msg, e);
		}

	}

	@Override
	public void onOpen(ServerHandshake arg0) {
		this.closed = false;
		logger.debug("connection opened to {} on {}:{} " + this.getURI().getRawPath(),this.uri.getHost(),this.uri.getPort());

	}

	public boolean isClosed() {
		return this.closed;
		
	}
}
