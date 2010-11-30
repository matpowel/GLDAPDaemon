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
package org.gldapdaemon.standalone;

import org.gldapdaemon.core.Configurator;

/**
 * GCALDAEMON STARTER (standalone application)
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class Main extends Thread {

	// --- GLOBAL CONFIGURATOR OF GCALDAEMON ---

	private static Configurator configurator;

	// --- APPLICATION STARTER ---

	public static final void main(String[] args) {

		// Init program
		Runtime.getRuntime().addShutdownHook(new Main());
		try {
			String configPath = null;
			byte runMode = Configurator.MODE_DAEMON;
			boolean userHome = false;
			if (args != null && args.length > 0) {
				String arg;
				for (int i = 0; i < args.length; i++) {
					arg = args[i];
					if (arg.startsWith("-")) {
						arg = arg.substring(1);
					}
					if (arg.equalsIgnoreCase("userhome")) {
						userHome = true;
						continue;
					}
					if (arg.equalsIgnoreCase("ondemand")
							|| arg.equalsIgnoreCase("runonce")) {
						runMode = Configurator.MODE_RUNONCE;
						continue;
					}
					if (arg.equalsIgnoreCase("configeditor")) {
						runMode = Configurator.MODE_CONFIGEDITOR;
						continue;
					}
					if (arg.indexOf('/') != -1 || arg.indexOf('\\') != -1
							|| arg.endsWith(".cfg")) {
						configPath = args[i];
					}
				}
			}
			configurator = new Configurator(configPath, null, userHome, runMode);
		} catch (Exception fatalError) {
			try {
				Thread.sleep(1000);
			} catch (Exception ignored) {
			}
			System.err.println("FATAL | Service terminated!");
			fatalError.printStackTrace();
		}
	}

	public final void run() {
		try {
			if (configurator != null) {
				configurator.interrupt();
				configurator = null;
			}
		} catch (Throwable ignored) {
		}
	}

}
