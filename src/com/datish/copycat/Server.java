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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.Formatter;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.io.IOUtils;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datish.copycat.events.VolumeEvent;
import com.datish.copycat.util.SDFSHttpClient;
import com.datish.copycat.util.SDFSHttpMsgException;
import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class Server implements Runnable {
	public static String PERSISTENCE_PATH = "c:\\tmp\\";
	private int port;
	private String hostName;
	private String password;
	private boolean listen;
	private boolean update;
	private long volumeID;
	private WebSocketUploadListener ws = null;
	private static ConcurrentHashMap<Long, Server> servers = new ConcurrentHashMap<Long, Server>();
	private String baseURL;
	Logger logger = LoggerFactory.getLogger(Server.class);
	private ConcurrentHashMap<String, ReentrantLock> activeTasks = new ConcurrentHashMap<String, ReentrantLock>();
	ConcurrentMap<String, String> updateMap = null;
	DB db = null;
	UpdateProcessor up = null;
	Thread th = null;
	private static ReentrantLock iLock = new ReentrantLock(true);

	public Server(long volumeID, String hostName, int port, String password,
			boolean listen, boolean update) throws IOException {
		try {
			this.port = port;
			this.volumeID = volumeID;
			this.hostName = hostName;
			this.password = password;
			this.listen = listen;
			this.update = update;
			this.baseURL = "https://" + this.hostName + ":" + this.port
					+ "/?password=" + URLEncoder.encode(this.password, "UTF-8")
					+ "&";
			if (this.listen) {
				th = new Thread(this);
				th.start();
			}
		} catch (Exception e) {
			throw new IOException(e);
		}
		if (this.update) {
			WebSocketUploadListener.registerEvents(this);
			db = DBMaker
					.fileDB(PERSISTENCE_PATH + File.separator + this.volumeID
							+ ".db").closeOnJvmShutdown()
					// TODO memory mapped files enable here
					.make();
			updateMap = db
					.hashMap("map", Serializer.STRING, Serializer.STRING)
					.createOrOpen();

			up = new UpdateProcessor(this);
		}
		servers.put(this.volumeID, this);
		logger.info("added " + this.toString());
	}

	public String toString() {
		return "volid=" + volumeID + " hostname=" + hostName + " port=" + port
				+ " listen=" + listen + " update=" + update;
	}

	public static void attachShutDownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				System.out.println("Shutting down Servers");
				for (Entry<Long, Server> s : servers.entrySet()) {
					s.getValue().close();

				}
				System.out.println("Shutdown Servers");
			}
		});
		System.out.println("Shut Down Hook Attached.");
	}

	private ReentrantLock getLock(String st) {
		iLock.lock();
		try {
			ReentrantLock l = activeTasks.get(st);
			if (l == null) {
				l = new ReentrantLock(true);
				activeTasks.put(st, l);
			}
			return l;
		} finally {
			iLock.unlock();
		}
	}

	private void removeLock(String st) {
		iLock.lock();
		try {
			ReentrantLock l = activeTasks.get(st);
			try {

				if (l != null && !l.hasQueuedThreads()) {
					this.activeTasks.remove(st);
				}
			} finally {
				if (l != null)
					l.unlock();
			}
		} finally {
			iLock.unlock();
		}
	}

	public boolean wsListening() {
		if (this.listen && ws != null) {
			return !ws.isClosed();
		} else {
			return false;
		}
	}

	public long getVolumeID() {
		return this.volumeID;
	}

	public String getBaseURL() {
		return "https://" + hostName + ":" + port + "/";
	}

	public int getPort() {
		return this.port;
	}

	public String getPassword() {
		return this.password;
	}

	public String getHostName() {
		return this.hostName;
	}

	public boolean isUpdate() {
		return this.update;
	}

	public boolean isListen() {
		return this.listen;
	}

	@Subscribe
	@AllowConcurrentEvents
	public void volumeEvent(VolumeEvent evt) {
		ReentrantLock l = this.getLock(evt.getTarget());
		l.lock();
		try {
			this.updateMap.put(evt.getTarget(), evt.getJsonString());
		} finally {
			this.db.commit();
			removeLock(evt.getTarget());
		}
	}

	@Override
	public void run() {
		for (;;) {
			try {
				if (!this.wsListening()) {
					try {
						ws.close();
						ws = null;
					} catch (Exception e) {

					}
					ws = new WebSocketUploadListener(hostName, port, password);
					ws.connect(hostName, port);
				}
				Thread.sleep(10000);
			} catch (InterruptedException e) {

			} catch (Exception e) {
				logger.error("unable run thread ", e);
			}
		}
	}

	public void close() {
		if (this.update && db != null) {
			try {
				up.close();
				db.commit();
				db.close();
			} catch (Throwable e) {

			}
			db = null;

		}
		if (this.listen && ws != null) {
			try {
				th.interrupt();
				ws.close();
			} catch (Exception e) {

			}
		}
	}

	public static void main(String[] args) throws IOException,
			InterruptedException {
		if (args.length < 1) {
			System.err.println("config file path must be selected");
			System.exit(-1);
		}
		
		InputStream is = new FileInputStream(args[0]);
		String jsonTxt = IOUtils.toString(is, "UTF-8");
		JsonParser parser = new JsonParser();
		JsonObject cfg = parser.parse(jsonTxt).getAsJsonObject();
		if(cfg.has("debug") && cfg.get("debug").getAsBoolean()) {
			System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY,
					"TRACE");
		}
		PERSISTENCE_PATH = cfg.get("persist-path").getAsString();
		if(!new File(PERSISTENCE_PATH).exists()) {
			new File(PERSISTENCE_PATH).mkdirs();
		}
		JsonArray jar = cfg.getAsJsonArray("servers");
		for (int i = 0; i < jar.size(); i++) {
			try {
				JsonObject obj = jar.get(i).getAsJsonObject();
				int port = obj.get("port").getAsInt();
				long volid = obj.get("volumeid").getAsLong();
				String hostName = obj.get("host").getAsString();
				String password = obj.get("password").getAsString();
				boolean listen = obj.get("listen").getAsBoolean();
				boolean update = obj.get("update").getAsBoolean();
				new Server(volid, hostName, port, password, listen, update);
			} catch (Exception e) {
				System.out.println("Unable to attach server");
				e.printStackTrace();
			}
		}
		Server.attachShutDownHook();
		if (args.length > 1) {
			System.out
					.println("press ENTER to call System.exit() and run the shutdown routine.");
			try {
				System.in.read();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			System.exit(0);
		} else {
			while (servers.size() > 0) {
				Thread.sleep(10000);
			}
		}

	}

	private static class UpdateProcessor implements Runnable {
		Logger logger = LoggerFactory.getLogger(UpdateProcessor.class);
		Server s = null;
		Thread th = null;

		protected UpdateProcessor(Server s) {
			this.s = s;
			th = new Thread(this);
			th.start();
		}

		@Override
		public void run() {
			for (;;) {
				try {
					Thread.sleep(10000);
					synchronized (th) {
						Set<String> set = s.updateMap.keySet();
						for (String file : set) {
							ReentrantLock l = s.getLock(file);
							l.lock();
							try {
								VolumeEvent evt = new VolumeEvent(s.updateMap.get(file));
								if(evt.getVolumeID() != this.s.volumeID) {
								if (evt.isMFUpdate()) {
									try {
										StringBuilder sb = new StringBuilder();
										Formatter formatter = new Formatter(sb);
										logger.debug("Updating File [" + file
												+ "] ");
										formatter.format(
												"file=%s&cmd=cloudmfile&changeid=%s", file,evt.getChangeID());
										String url = s.baseURL + sb.toString();
										formatter.close();
										logger.debug("sending " + url);
										SDFSHttpClient.getResponse(url);
										set.remove(file);
										s.updateMap.remove(file);
									} catch (Exception e) {
										logger.debug(
												"unable to update " + file + " on " + this.s.hostName + ":" + this.s.port, e);
									}
								} else if (evt.isMFDelete()) {
									try {
										StringBuilder sb = new StringBuilder();
										Formatter formatter = new Formatter(sb);
										logger.debug("Deleting File [" + file
												+ "] ");
										formatter.format(
												"file=%s&cmd=%s&options=%s&changeid=%s",
												file, "deletefile", "",evt.getChangeID());
										String url = s.baseURL + sb.toString();
										formatter.close();
										logger.debug("sending " + url);
										SDFSHttpClient.getResponse(url);
										set.remove(file);
										s.updateMap.remove(file);
									} catch (SDFSHttpMsgException e) {
										set.remove(file);
										s.updateMap.remove(file);
									} catch (Exception e) {
										logger.debug(
												"unable to delete " + file+ " on " + this.s.hostName + ":" + this.s.port, e);
									}
								} else if (evt.isDBUpdate()) {
									try {
										StringBuilder sb = new StringBuilder();
										Formatter formatter = new Formatter(sb);
										logger.debug("Updating File [" + file
												+ "] ");
										formatter
												.format("file=%s&cmd=clouddbfile&changeid=%s",
														file,evt.getChangeID());
										String url = s.baseURL + sb.toString();
										formatter.close();
										logger.debug("sending " + url);
										SDFSHttpClient.getResponse(url);
										set.remove(file);
										s.updateMap.remove(file);
									} catch (Exception e) {
										logger.debug(
												"unable to delete " + file+ " on " + this.s.hostName + ":" + this.s.port, e);
									}
								} else {
									set.remove(file);
									s.updateMap.remove(file);
								}
								}else {
									s.updateMap.remove(file);
									logger.debug("ignoring");
								}

							} finally {
								s.db.commit();
								s.removeLock(file);
							}
						}
					}
				} catch (InterruptedException e) {

				} catch (Exception e) {
					logger.error("unable run update thread ", e);
				}

			}

		}

		public void close() {
			synchronized (th) {
				th.interrupt();
			}
		}
	}

}
