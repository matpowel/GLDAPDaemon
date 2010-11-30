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
package org.gldapdaemon.core;

/**
 * Simple hostname / IP-address pattern container. Sample patterns:
 * 
 * <li>127.0.0.1
 * <li>234.11.*
 * <li>*.14.13
 * <li>*.mydomain.com
 * <li>users.mydomain.*
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class FilterMask {

	// --- CONSTANTS ---

	private static final byte MODE_EQUALS = 0;
	private static final byte MODE_START = 1;
	private static final byte MODE_END = 2;
	private static final byte MODE_ALL = 3;

	private static final char JOKER_CHAR = '*';

	// --- VARIABLES ---

	private final boolean ignoreCase;

	private String partString;
	private byte mode = MODE_EQUALS;

	// --- CONSTRUCTOR ---

	FilterMask(String pattern, boolean ignoreCase) throws Exception {
		if (pattern == null || pattern.length() == 0) {
			throw new NullPointerException("mask == null");
		}
		this.ignoreCase = ignoreCase;
		int pos = pattern.indexOf(JOKER_CHAR);
		if (pos == -1) {
			partString = pattern;
		} else {
			if (pos == 0) {
				if (pattern.length() == 1) {
					mode = MODE_ALL;
				} else {
					partString = pattern.substring(1);
					mode = MODE_END;
				}
			} else {
				if (pos == pattern.length() - 1) {
					partString = pattern.substring(0, pattern.length() - 1);
					mode = MODE_START;
				} else {
					throw new IllegalArgumentException(
							"Malformed mask syntax: " + pattern);
				}
			}
		}
		if (ignoreCase) {
			partString = partString.toLowerCase();
		}
	}

	// --- PATTERN VERIFIER ---

	public final boolean match(String text) {
		if (mode == MODE_ALL) {
			return true;
		}
		if (ignoreCase) {
			text = text.toLowerCase();
		}
		if (mode == MODE_EQUALS) {
			return text.equals(partString);
		}
		if (mode == MODE_START) {
			return text.startsWith(partString);
		}
		return text.endsWith(partString);
	}

}
