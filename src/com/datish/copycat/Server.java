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
import java.net.InetAddress;
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
import org.slf4j.LoggerFactory;

import com.datish.copycat.events.VolumeEvent;
import com.datish.copycat.util.MountService;
import com.datish.copycat.util.SDFSHttpClient;
import com.datish.copycat.util.SDFSHttpMsgException;
import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.util.FileSize;
import ch.qos.logback.core.util.StatusPrinter;

public class Server implements Runnable {
	public static String version = null;
	public static String ts = null;
	public static String PERSISTENCE_PATH = "c:\\tmp\\";
	private int port;
	private String hostName;
	private String password;
	private boolean listen;
	private boolean update;
	private long volumeID;
	private boolean useSSL;
	private boolean mountVolume = false;
	private boolean unmountVolume = false;
	private String volumeName = null;
	private String mountPoint = null;
	private WebSocketUploadListener ws = null;
	protected static ConcurrentHashMap<Long, Server> servers = new ConcurrentHashMap<Long, Server>();
	private String baseURL;
	private boolean closed = true;
	Logger logger = null;
	private ConcurrentHashMap<String, ReentrantLock> activeTasks = new ConcurrentHashMap<String, ReentrantLock>();
	ConcurrentMap<String, String> updateMap = null;
	DB db = null;
	UpdateProcessor up = null;
	Thread th = null;
	private static ReentrantLock iLock = new ReentrantLock(true);
	long updateTime = 20000;
	long checkTime = 5000;

	public Server(long volumeID, String hostName, int port, String password, boolean listen, boolean update,
			boolean useSSL) throws IOException {
		try {
			logger = (Logger) LoggerFactory.getLogger("server-" + Long.toString(volumeID));

			this.port = port;
			this.volumeID = volumeID;
			this.hostName = hostName;
			this.password = password;
			this.listen = listen;
			this.update = update;
			this.useSSL = useSSL;

			String proto = "https";
			if (!useSSL)
				proto = "http";
			this.baseURL = proto + "://" + this.hostName + ":" + this.port + "/?password="
					+ URLEncoder.encode(this.password, "UTF-8") + "&";

		} catch (Exception e) {
			throw new IOException(e);
		}
		servers.put(this.volumeID, this);
		logger.info("added " + this.toString());
	}

	public void startServer() throws IOException {
		synchronized (this) {
			try {
				if (this.closed) {
					if (this.mountVolume && MountService.isThisMyIpAddress(InetAddress.getByName(this.hostName))) {
						StringBuilder sb = new StringBuilder();
						Formatter formatter = new Formatter(sb);
						logger.debug("Checking if volume is up for " + this.volumeName);
						formatter.format("file=%s&cmd=dse-info", "null");
						String url = baseURL + sb.toString();
						formatter.close();
						logger.debug("sending " + url);
						try {
							SDFSHttpClient.getResponse(url);
						} catch (Exception e) {
							logger.info("Attemping to mount volume " + this.volumeName);
							try {
								MountService.mountHost(this.volumeName, this.mountPoint, logger);
							} catch (Exception e1) {
								logger.error("unable to mount volume " + this.volumeName, e1);
							}
						}
					}
					if (this.listen) {
						th = new Thread(this);
						th.start();
					}
					if (this.update) {
						WebSocketUploadListener.registerEvents(this);
						db = DBMaker.fileDB(PERSISTENCE_PATH + File.separator + this.volumeID + ".db")
								.closeOnJvmShutdown().transactionEnable()
								// TODO memory mapped files enable here
								.make();
						updateMap = db.hashMap("map", Serializer.STRING, Serializer.STRING).createOrOpen();

						up = new UpdateProcessor(this);
					}
					logger.info("Server Started for " + this.volumeID);
				} else {
					System.out.println("Service already started");
				}
			} catch (Exception e) {
				throw new IOException(e);
			}
			this.closed = false;
		}
	}

	public String toString() {
		return "volid=" + volumeID + " hostname=" + hostName + " port=" + port + " listen=" + listen + " update="
				+ update;
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
			if(this.updateMap.containsKey(evt.getTarget()) && (evt.isMFUpdate() || evt.isDBUpdate())) {
				VolumeEvent _evt = new VolumeEvent(this.updateMap.get(evt.getTarget()));
				if(!_evt.isMFDelete())
					this.updateMap.put(evt.getTarget(), evt.getJsonString());
			}else {
				this.updateMap.put(evt.getTarget(), evt.getJsonString());
			}
		} finally {
			try {
				this.db.commit();
			} catch (Exception e) {
			}
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
					String cs = "wss";
					if (!this.useSSL)
						cs = "ws";
					ws = new WebSocketUploadListener(hostName, port, password, cs);
					ws.connect(hostName, port);
				}
			} catch (InterruptedException e) {

			} catch (Exception e) {
				logger.error("unable run thread ", e);
			} finally {
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e) {

				}
			}
		}
	}

	public void close() {
		synchronized (this) {
			this.closed = true;
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
			try {
				if (this.unmountVolume && MountService.isThisMyIpAddress(InetAddress.getByName(this.hostName))) {
					StringBuilder sb = new StringBuilder();
					Formatter formatter = new Formatter(sb);
					logger.debug("Checking if volume is up for " + this.volumeName);
					formatter.format("file=%s&cmd=shutdown", "null");
					String url = baseURL + sb.toString();
					formatter.close();
					logger.debug("sending " + url);
					try {
						SDFSHttpClient.getResponse(url);
					} catch (Exception e) {

					}
				}
			} catch (Exception e) {

			}

		}
	}

	public static void setup(String[] args) throws IOException, InterruptedException {
		if (args.length < 1) {
			System.err.println("config file path must be selected");
			System.exit(-1);
		}
		

		InputStream is = new FileInputStream(args[0]);
		boolean ldbg = false;

		String jsonTxt = IOUtils.toString(is, "UTF-8");
		JsonParser parser = new JsonParser();
		JsonObject cfg = parser.parse(jsonTxt).getAsJsonObject();

		PERSISTENCE_PATH = cfg.get("persist-path").getAsString();
		if (!new File(PERSISTENCE_PATH).exists()) {
			new File(PERSISTENCE_PATH).mkdirs();
		}
		String logFile = PERSISTENCE_PATH + File.separator + "oddcopycat.log";
		if (cfg.has("debug") && cfg.get("debug").getAsBoolean()) {
			ldbg = cfg.get("debug").getAsBoolean();
		}
		if (cfg.has("log-file")) {
			logFile = cfg.get("log-file").getAsString();

		}

		startFileLogging(logFile, ldbg);
		Logger logger = (Logger) LoggerFactory.getLogger("initialization");
		logger.info("Starting CopyCat Version " + Server.version + " build time " + Server.ts);
		JsonArray jar = cfg.getAsJsonArray("servers");
		for (int i = 0; i < jar.size(); i++) {
			try {
				boolean useSSL = true;
				JsonObject obj = jar.get(i).getAsJsonObject();
				int port = obj.get("port").getAsInt();
				long volid = obj.get("volumeid").getAsLong();
				String hostName = obj.get("host").getAsString();
				String password = obj.get("password").getAsString();
				boolean listen = obj.get("listen").getAsBoolean();
				boolean update = obj.get("update").getAsBoolean();
				if (obj.has("useSSL"))
					useSSL = obj.get("useSSL").getAsBoolean();
				Server s = new Server(volid, hostName, port, password, listen, update, useSSL);
				if (obj.has("update-interval"))
					s.updateTime = obj.get("update-interval").getAsLong();
				if (obj.has("check-interval"))
					s.checkTime = obj.get("check-interval").getAsLong();
				if(obj.has("automount")) {
					s.mountVolume = obj.get("automount").getAsBoolean();
				}
				if(obj.has("autounmount")) {
					s.unmountVolume = obj.get("autounmount").getAsBoolean();
				}
				if(obj.has("volumename")) {
					s.volumeName = obj.get("volumename").getAsString();
				}
				if(obj.has("mountpoint")) {
					s.mountPoint = obj.get("mountpoint").getAsString();
				}
			} catch (Exception e) {
				System.out.println("Unable to attach server");
				e.printStackTrace();
			}
		}
		Server.attachShutDownHook();

	}

	public static Logger startFileLogging(String logFilePath, boolean debug) {

		LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
		ch.qos.logback.classic.Logger rootLogger = loggerContext
				.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
		loggerContext.reset();
		if (debug)
			rootLogger.setLevel(Level.DEBUG);
		else
			rootLogger.setLevel(Level.INFO);

		RollingFileAppender<ILoggingEvent> rfAppender = new RollingFileAppender<ILoggingEvent>();
		rfAppender.setContext(loggerContext);
		rfAppender.setFile(logFilePath);

		FixedWindowRollingPolicy fwRollingPolicy = new FixedWindowRollingPolicy();
		fwRollingPolicy.setContext(loggerContext);
		fwRollingPolicy.setFileNamePattern(logFilePath + "-%i.log.zip");
		fwRollingPolicy.setParent(rfAppender);
		fwRollingPolicy.setMaxIndex(5);
		fwRollingPolicy.start();

		SizeBasedTriggeringPolicy<ILoggingEvent> triggeringPolicy = new SizeBasedTriggeringPolicy<ILoggingEvent>();
		triggeringPolicy.setMaxFileSize(FileSize.valueOf("10MB"));
		triggeringPolicy.start();

		PatternLayoutEncoder encoder = new PatternLayoutEncoder();
		encoder.setContext(loggerContext);
		encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
		encoder.start();

		rfAppender.setEncoder(encoder);
		rfAppender.setRollingPolicy(fwRollingPolicy);
		rfAppender.setTriggeringPolicy(triggeringPolicy);
		rfAppender.start();

		rootLogger.addAppender(rfAppender);

		// generate some output

		StatusPrinter.print(loggerContext);
		return rootLogger;

	}

	protected static void startServers() {
		System.out.println("Starting [" + servers.size() + "] Server Listeners");
		for (Server s : servers.values()) {
			try {
				s.startServer();
			} catch (Exception e) {
				System.out.println("Unable to attach server");
				e.printStackTrace();
			}
		}
		System.out.println("Servers Started");
	}

	public static void main(String[] args) throws IOException, InterruptedException {

		setup(args);
		startServers();
		if (args.length > 1) {
			System.out.println("press ENTER to call System.exit() and run the shutdown routine.");
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
		Logger logger = null;
		Server s = null;
		Thread th = null;

		protected UpdateProcessor(Server s) {
			logger = (Logger) LoggerFactory.getLogger("update-" + s.volumeID);

			this.s = s;
			th = new Thread(this);
			th.start();
		}

		@Override
		public void run() {
			for (;;) {
				try {
					Thread.sleep(s.checkTime);
					synchronized (th) {
						Set<String> set = s.updateMap.keySet();
						for (String file : set) {
							ReentrantLock l = s.getLock(file);
							l.lock();
							try {
								VolumeEvent evt = new VolumeEvent(s.updateMap.get(file));
								long ts = System.currentTimeMillis() - evt.getInternalTS();

								if (ts > s.updateTime) {
									if (evt.getVolumeID() != this.s.volumeID) {
										if (evt.isMFUpdate()) {
											try {
												StringBuilder sb = new StringBuilder();
												Formatter formatter = new Formatter(sb);
												logger.debug("Updating File [" + file + "] ");
												formatter.format("file=%s&cmd=cloudfile&overwrite=true&changeid=%s",
														URLEncoder.encode(file, "UTF-8"), evt.getChangeID());
												String url = s.baseURL + sb.toString();
												formatter.close();
												logger.debug("sending " + url);
												SDFSHttpClient.getResponse(url);
												set.remove(file);
												s.updateMap.remove(file);
											} catch (Exception e) {
												logger.debug("unable to update " + file + " on " + this.s.hostName + ":"
														+ this.s.port, e);
											}
										} else if (evt.isMFDelete()) {
											try {
												StringBuilder sb = new StringBuilder();
												Formatter formatter = new Formatter(sb);
												logger.debug("Deleting File [" + file + "] ");
												formatter.format(
														"file=%s&cmd=%s&options=%s&changeid=%s&retentionlock=true&localonly=true",
														URLEncoder.encode(file, "UTF-8"), "deletefile", "",
														evt.getChangeID());
												String url = s.baseURL + sb.toString();
												formatter.close();
												logger.debug("sending " + url);
												SDFSHttpClient.getResponse(url);
												set.remove(file);
												s.updateMap.remove(file);
											} catch (SDFSHttpMsgException e) {
												logger.debug(
														"Delete File [" + file + "] failed because " + e.getMessage());
												set.remove(file);
												s.updateMap.remove(file);
											} catch (Exception e) {
												logger.debug("unable to delete mf " + file + " on " + this.s.hostName
														+ ":" + this.s.port, e);
											}
										} else if (evt.isDBUpdate()) {
											try {
												StringBuilder sb = new StringBuilder();
												Formatter formatter = new Formatter(sb);
												logger.debug("Updating File [" + file + "] ");
												formatter.format("file=%s&cmd=cloudfile&overwrite=true&changeid=%s",
														URLEncoder.encode(file, "UTF-8"), evt.getChangeID());
												String url = s.baseURL + sb.toString();
												formatter.close();
												logger.debug("sending " + url);
												SDFSHttpClient.getResponse(url);
												set.remove(file);
												s.updateMap.remove(file);
											} catch (Exception e) {
												logger.debug("unable to update ddb " + file + " on " + this.s.hostName
														+ ":" + this.s.port, e);
											}
										} else {
											set.remove(file);
											s.updateMap.remove(file);
										}
									} else {
										s.updateMap.remove(file);
										logger.debug("ignoring");
									}
								} else {
									logger.debug("Waiting on event time diff =" + ts);

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
