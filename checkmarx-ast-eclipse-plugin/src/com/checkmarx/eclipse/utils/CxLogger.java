package com.checkmarx.eclipse.utils;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/**
 * Class responsible to add entries to Eclipse Error Log perspective
 * 
 * @author HugoMa
 *
 */
public class CxLogger {

	private static final Bundle BUNDLE = FrameworkUtil.getBundle(CxLogger.class);
	private static final ILog LOGGER = Platform.getLog(BUNDLE);
	
	/**
	 * Add entry as error
	 * 
	 * @param msg
	 * @param e
	 */
	public static void error(String msg, Exception e) {
		log(Status.ERROR, msg, e);
	}
	
	/**
	 * Add entry as warning
	 * 
	 * @param msg
	 */
	public static void warning(String msg) {
		log(Status.WARNING, msg, null);
	}
	
	/**
	 * Add entry as info
	 * 
	 * @param msg
	 */
	public static void info(String msg) {
		log(Status.INFO, msg, null);
	}
	
	/**
	 * Add entry to Error Log
	 * 
	 * @param status
	 * @param msg
	 * @param e
	 */
	private static void log(int status, String msg, Exception e) {
		LOGGER.log(new Status(status, BUNDLE.getSymbolicName(), msg, e));
	}
}
