package com.datish.copycat.util;
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
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HostnameVerifier;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class SDFSHttpClient {
static CloseableHttpClient httpClient = null;
	
	
	static {
		try {
			httpClient = httpClientTrustingAllSSLCerts();
		} catch (Throwable e) {
			e.printStackTrace();

		}
	}
	
	public static void init() {
		
	}

	private static CloseableHttpClient httpClientTrustingAllSSLCerts()
			throws NoSuchAlgorithmException, KeyManagementException, KeyStoreException {

		SSLContextBuilder builder = new SSLContextBuilder();
		builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
		HostnameVerifier hostnameVerifier = NoopHostnameVerifier.INSTANCE;
		SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
				builder.build(),hostnameVerifier);
		Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder
		        .<ConnectionSocketFactory> create().register("https", sslsf)
		        .build();

		PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(
		        socketFactoryRegistry);
		
		
		RequestConfig config = RequestConfig.custom()
				  .setConnectTimeout(10000)
				  .setConnectionRequestTimeout(10000)
				  .setSocketTimeout(3000).build();
		CloseableHttpClient httpclient = HttpClients.custom().setDefaultRequestConfig(config)
		        .setConnectionManager(cm).build();
		return httpclient;
	}
	public static void getResponse(String url) throws IOException, SDFSHttpMsgException {
		try {

			HttpGet httpGet = new HttpGet(url);
			CloseableHttpResponse response = httpClient.execute(httpGet);
			try {
				if(response.getStatusLine().getStatusCode() != 200) {
					throw new IOException("Status returned = " + response.getStatusLine().getStatusCode() + " " + response.getStatusLine().getReasonPhrase());
				}
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				DocumentBuilder db = dbf.newDocumentBuilder();
				HttpEntity entity = response.getEntity();
				Document doc = db.parse(entity.getContent());
				doc.getDocumentElement().normalize();
				Element root = doc.getDocumentElement();
				if (root.getAttribute("status").equals("failed"))
					throw new SDFSHttpMsgException(root.getAttribute("msg"));
			} finally {
				response.close();
			}
		} catch(SDFSHttpMsgException e) {
			throw e;
		}catch (Exception e) {
		
			throw new IOException(e);
		}
	}

}
