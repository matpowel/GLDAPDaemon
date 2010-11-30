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
package org.gldapdaemon.service;

import org.gldapdaemon.core.Configurator;
import org.tanukisoftware.wrapper.WrapperListener;
import org.tanukisoftware.wrapper.WrapperManager;

/**
 * GCALDAEMON STARTER (Windows service)
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class Main implements WrapperListener {

    // --- GLOBAL CONFIGURATOR OF GCALDAEMON ---
    private Configurator configurator;

    // --- SERVICE METHODS ---
    public static final void main(String[] args) {
        WrapperManager.start(new Main(), args);
    }

    public final Integer start(String[] args) {
        if (configurator == null) {
            try {
                String configPath = null;
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
                        if (arg.indexOf('/') != -1 || arg.indexOf('\\') != -1
                                || arg.endsWith(".cfg")) {
                            configPath = args[i];
                        }
                    }
                }
                configurator = new Configurator(configPath, null, userHome,
                        Configurator.MODE_DAEMON);
            } catch (Exception fatalError) {
                WrapperManager.log(WrapperManager.WRAPPER_LOG_LEVEL_FATAL,
                        String.valueOf(fatalError));
            }
        }
        return null;
    }

    public final void controlEvent(int event) {
        if (WrapperManager.isControlledByNativeWrapper()) {
        } else {
            if ((event == WrapperManager.WRAPPER_CTRL_C_EVENT)
                    || (event == WrapperManager.WRAPPER_CTRL_CLOSE_EVENT)
                    || (event == WrapperManager.WRAPPER_CTRL_SHUTDOWN_EVENT)) {
                WrapperManager.stop(0);
            }
        }
    }

    public final int stop(int exitCode) {
        if (configurator != null) {

            // Fast shutdown
            WrapperManager.signalStopping(exitCode);
            configurator.interrupt();
            configurator = null;
            WrapperManager.signalStopped(exitCode);
        }
        return exitCode;
    }
}
