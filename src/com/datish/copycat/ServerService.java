package com.datish.copycat;

import java.util.Properties;

import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonInitException;

public class ServerService implements Daemon {

	@Override
	public void destroy() {
		// TODO Auto-generated method stub

	}

	@Override
	public void init(DaemonContext arg0) throws DaemonInitException, Exception {
		Server.setup(arg0.getArguments());

	}

	@Override
	public void start() throws Exception {
		Properties props = new Properties();
	    try 
	    {
			props.load(this.getClass().getResourceAsStream("/version.properties"));
	        Server.version = props.getProperty("version");
	        Server.ts = props.getProperty("timestamp");
	        
	    } catch (Exception e) 
	    {
	        e.printStackTrace();
	    }
		

		Server.startServers();

	}

	@Override
	public void stop() throws Exception {
		System.exit(0);

	}

}
