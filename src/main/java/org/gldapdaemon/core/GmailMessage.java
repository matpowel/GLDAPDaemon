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
 * Pooled Gmail connection.
 * 
 * Created: Jan 03, 2008 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class GmailMessage {

	// --- VARIABLES ---

	public String from;
	public String subject;
	public String memo;

}
