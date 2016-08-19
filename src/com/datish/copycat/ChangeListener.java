package com.datish.copycat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.java_websocket.WebSocketImpl;
import org.java_websocket.client.DefaultSSLWebSocketClientFactory;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;

import com.datish.copycat.util.EasyX509TrustManager;

class WebSocketChatClient extends WebSocketClient {
	

	public WebSocketChatClient( URI serverUri ) {
		super( serverUri, new Draft_17()  );
	}

	@Override
	public void onOpen( ServerHandshake handshakedata ) {
		System.out.println( "Connected" );

	}

	@Override
	public void onMessage( String message ) {
		System.out.println( "got: " + message );

	}

	@Override
	public void onClose( int code, String reason, boolean remote ) {
		System.out.println( "Disconnected" );

	}

	@Override
	public void onError( Exception ex ) {
		ex.printStackTrace();

	}

}
public class ChangeListener {
	
	
	/*
	 * Keystore with certificate created like so (in JKS format):
	 *
	 *keytool -genkey -validity 3650 -keystore "keystore.jks" -storepass "storepassword" -keypass "keypassword" -alias "default" -dname "CN=127.0.0.1, OU=MyOrgUnit, O=MyOrg, L=MyCity, S=MyRegion, C=MyCountry"
	 */
	public static void main( String[] args ) throws Exception {
		WebSocketImpl.DEBUG = true;

		WebSocketChatClient chatclient = new WebSocketChatClient( new URI( "wss://13.91.96.131:6442/uploadsocket?password=pla" ));

		SSLContext sslContext = null;
		sslContext = SSLContext.getInstance( "TLS" );
		 

		sslContext.init( null, new TrustManager[] {new EasyX509TrustManager(null)}, null );
		// sslContext.init( null, null, null ); // will use java's default key and trust store which is sufficient unless you deal with self-signed certificates

		chatclient.setWebSocketFactory( new DefaultSSLWebSocketClientFactory(sslContext));

		chatclient.connectBlocking();

		
		BufferedReader reader = new BufferedReader( new InputStreamReader( System.in ) );
		while ( true ) {
			String line = reader.readLine();
			if( line.equals( "close" ) ) {
				chatclient.close();
			} else {
				chatclient.send( line );
			}
		}
	}
}
