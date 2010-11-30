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

import java.io.InputStreamReader;
import java.io.LineNumberReader;

/**
 * Simple password encoder. Note, this is not meant to keep your password
 * secure. It is designed to stop your password from being visible in plain text
 * form inside your GCALDaemon configuration file (gcal-daemon.cfg).
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class PasswordEncoder {

	public static final void main(String[] args) {
		try {
			System.out.print("Your Google password: ");
			InputStreamReader isr = new InputStreamReader(System.in);
			LineNumberReader lnr = new LineNumberReader(isr);
			String password = lnr.readLine();
			if (password != null) {
				password = password.trim();
				if (password.length() == 0) {
					return;
				}
				String encoded = encodePassword(password);
				System.out.println();
				System.out.println("Original password: "
						+ StringUtils.decodePassword(encoded));
				System.out.println("Encoded  password: " + encoded);
				System.out.println();
				System.out
						.println("Sample configuration options for GCALDaemon:");
				System.out.println();
				System.out.println("file.google.password=" + encoded);
				System.out.println("ldap.google.password=" + encoded);
				System.out.println();
				System.out.println("notifier.google.password=" + encoded);
				System.out.println("sendmail.google.password=" + encoded);
				System.out.println("mailterm.google.password=" + encoded);
				System.out.println();
				System.out.println();
				Thread.sleep(5000);
			}
		} catch (Exception anyError) {
			anyError.printStackTrace();
		}
	}

	public static final String encodePassword(String password) throws Exception {
		byte[] bytes = StringUtils.encodeString(password, StringUtils.UTF_8);
		String base64 = StringUtils.encodeBASE64(bytes);
		StringBuffer buffer = new StringBuffer(base64);
		String seed = Long.toString(System.currentTimeMillis());
		seed = seed.substring(seed.length() - 3);
		String encoded = seed + buffer.reverse().toString();
		return encoded.replace('=', '$');
	}

}
