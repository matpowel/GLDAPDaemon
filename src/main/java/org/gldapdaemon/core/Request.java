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
 * Request container of a sort of calendar application (e.g.
 * Thunderbird/Lightning).
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public class Request {

	/**
	 * GET, PUT, etc
	 */
	public String method;

	/**
	 * URL of iCalendar file
	 */
	public String url;

	/**
	 * Google username (full Gmail address)
	 */
	public String username;

	/**
	 * Google password
	 */
	public String password;

	/**
	 * Modified iCalendar file
	 */
	public byte[] body;

	/**
	 * Local calendar file path (optional)
	 */
	public String filePath;

}
