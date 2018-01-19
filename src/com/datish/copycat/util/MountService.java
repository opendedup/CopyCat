package com.datish.copycat.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;

import ch.qos.logback.classic.Logger;

public class MountService {
	public static boolean isThisMyIpAddress(InetAddress addr) {
		// Check if the address is a valid special local or loop back
		if (addr.isAnyLocalAddress() || addr.isLoopbackAddress())
			return true;

		// Check if the address is defined on any interface
		try {
			return NetworkInterface.getByInetAddress(addr) != null;
		} catch (SocketException e) {
			return false;
		}
	}

	public static void checkMount(String server, String volume, String mountpoint, Logger logger) {
		try {
			if (isThisMyIpAddress(InetAddress.getByName(server))) {
				mountHost(volume,mountpoint,logger);
			}
		} catch (Exception e) {
			logger.error("unable to check mount for " + server + " " + volume + " " + mountpoint, e);
		}
	}

	public static int mountHost(String volume, String mountpoint, Logger logger) throws InterruptedException, IOException {
		int status = -1;
		if (OSValidator.isWindows()) {
			String process = "mountsdfs -v " + volume + " -m " + mountpoint;
			logger.debug("executing " + process);
			Process p = Runtime.getRuntime().exec(process);
			status = p.waitFor();
		} else {
			ArrayList<String> al = new ArrayList<String>();
			String commandLine = "/usr/share/sdfs/mount.sdfs " + volume + " " + mountpoint;
			al.add("/bin/bash");
			al.add("-c");
			al.add(commandLine);
			String st = al.get(0) + " " + al.get(1) + " " + al.get(2);
			logger.debug("executing " + st);
			ProcessBuilder b = new ProcessBuilder(al);
			Process p = b.start();
			status = p.waitFor();

		}
		logger.debug("process returned " + status);
		return status;
	}

	public static void main(String[] args) {
		boolean isMyDesiredIp = false;
		try {
			isMyDesiredIp = isThisMyIpAddress(InetAddress.getByName("192.168.0.114")); // "localhost" for localhost
		} catch (UnknownHostException unknownHost) {
			unknownHost.printStackTrace();
		}
		System.out.println(isMyDesiredIp);
	}

}
