//
// GCALDaemon is an OS-independent Java program that offers two-way
// synchronization between Google Calendar and various iCalalendar (RFC 2445)
// compatible calendar applications (Sunbird, Rainlendar, iCal, Lightning, etc).
//
// Apache License
// Version 2.0, January 2004
// http://www.apache.org/licenses/
//
// Project home:
// http://gcaldaemon.sourceforge.net
//
package org.gldapdaemon.logger;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.commons.logging.Log;

/**
 * Simple implementation of Log that sends all enabled log messages, for all
 * defined loggers, to System.out.
 * 
 * Created: Jan 22, 2008 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class DefaultLog implements Log {

	// --- DATE FORMATTER ---

	private static final SimpleDateFormat timestamp = new SimpleDateFormat(
			"HH:mm:ss");

	// --- DEBUG FLAG ---

	private final boolean debugEnabled;

	// --- CONSTRUCTOR ---

	/**
	 * Construct a DefaultLog with given name.
	 * 
	 * @param name
	 *            log name
	 */
	public DefaultLog(String name) {
		if (name == null) {
			debugEnabled = false;
		} else {
			debugEnabled = name.startsWith("org.gldapdaemon");
		}
	}

	// --- SIMPLE INTERNAL LOGGER ---

	private static final synchronized void log(Object event, Throwable error) {
		System.out.print("[");
		System.out.print(timestamp.format(new Date()));
		System.out.print("] ");
		if (event == null) {
			if (error != null) {
				System.out.println(error.toString());
			}
		} else {
			System.out.println(event);
		}
		if (error != null) {
			error.printStackTrace();
		}
	}

	// --- LOG IMPLEMENTATION ---

	public final void trace(Object event) {
		if (debugEnabled) {
			log(event, null);
		}
	}

	public final void trace(Object event, Throwable error) {
		if (debugEnabled) {
			log(event, error);
		}
	}

	public final void debug(Object event) {
		if (debugEnabled) {
			log(event, null);
		}
	}

	public final void debug(Object event, Throwable error) {
		if (debugEnabled) {
			log(event, error);
		}
	}

	public final void error(Object event) {
		log(event, null);
	}

	public final void error(Object event, Throwable error) {
		log(event, error);
	}

	public final void fatal(Object event) {
		log(event, null);
	}

	public final void fatal(Object event, Throwable error) {
		log(event, error);
	}

	public final void info(Object event) {
		log(event, null);
	}

	public final void info(Object event, Throwable error) {
		log(event, error);
	}

	public final void warn(Object event) {
		log(event, null);
	}

	public final void warn(Object event, Throwable error) {
		log(event, error);
	}

	public final boolean isDebugEnabled() {
		return debugEnabled;
	}

	public final boolean isTraceEnabled() {
		return debugEnabled;
	}

	public final boolean isErrorEnabled() {
		return true;
	}

	public final boolean isFatalEnabled() {
		return true;
	}

	public final boolean isInfoEnabled() {
		return true;
	}

	public final boolean isWarnEnabled() {
		return true;
	}

}
