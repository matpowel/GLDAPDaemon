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
 * Response container of a sort of calendar application (e.g.
 * Thunderbird/Lightning).
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class Response {

	/**
	 * HTTP status code (e.g. 200 = OK)
	 */
	public int status;

	/**
	 * iCalendar file (or error message)
	 */
	public byte[] body;

	/**
	 * Content-type of the HTTP response
	 */
	public String contentType = "text/calendar; charset=utf-8";

}
