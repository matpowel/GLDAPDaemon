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

import java.util.Iterator;
import java.util.LinkedList;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Gmail connection pool.
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class GmailPool extends Thread {

    // --- CONSTANTS ---
    private static final long GMAIL_CONNECTION_TIMEOUT = (1000L * 60 * 5)
            - (1000L * 20);
    // --- LOGGER ---
    private static final Log log = LogFactory.getLog(GmailPool.class);
    // --- GMAIL CLIENT POOL ---
    private final LinkedList pool = new LinkedList();
    // --- SERVICE TYPES ---
    private final boolean ldap;

    // --- CONSTRUCTOR ---
    public GmailPool(ThreadGroup mainGroup, Configurator configurator) {
        super(mainGroup, "Gmail pool");
        setPriority(Thread.NORM_PRIORITY - 2);

        // Enable contact service
        ldap = configurator.getConfigProperty(Configurator.LDAP_ENABLED, false);

        // Start pooler
        start();
    }

    // --- BORROW OBJECT ---
    public final synchronized GmailEntry borrow(String username, String password)
            throws Exception {

        // Find entry
        long now = System.currentTimeMillis();
        Iterator entries = pool.iterator();
        GmailEntry entry;
        while (entries.hasNext()) {
            entry = (GmailEntry) entries.next();
            if (entry.username.equals(username)) {
                entries.remove();
                if (isTimeouted(now, entry)) {
                    disconnect(entry);
                } else {
                    log.debug("Gmail connection borrowed from the pool.");
                    entry.lastUsage = now;
                    return entry;
                }
            }
        }
        entry = new GmailEntry(ldap);
        entry.connect(username, password);
        entry.username = username;
        entry.lastUsage = now;
        log.debug("Gmail connection has been created successfully.");
        return entry;
    }

    // --- RECYCLE OBJECT ---
    public final synchronized void recycle(GmailEntry entry) {
        if (entry != null && entry.isConnected()) {
            long now = System.currentTimeMillis();
            if (isTimeouted(now, entry)) {
                disconnect(entry);
                return;
            }
            log.debug("Gmail connection released.");
            pool.addFirst(entry);
        }
    }

    // --- POOL CLEANUP ---
    public final void run() {
        for (;;) {
            try {
                // Check timeouts
                long now = System.currentTimeMillis();
                GmailEntry entry;
                synchronized (this) {
                    if (!pool.isEmpty()) {
                        Iterator entries = pool.iterator();
                        while (entries.hasNext()) {
                            entry = (GmailEntry) entries.next();
                            if (isTimeouted(now, entry)) {
                                entries.remove();
                                disconnect(entry);
                            }
                        }
                    }
                }

                // Wait
                sleep(10000);

            } catch (InterruptedException interrupt) {
                return;
            } catch (Exception poolException) {
                try {
                    log.warn("Unexpected pooling error!", poolException);
                    sleep(10000);
                } catch (Exception ignored) {
                    return;
                }
            }
        }
    }

    private static final boolean isTimeouted(long now, GmailEntry entry) {
        if (entry == null) {
            return true;
        }
        return now - entry.lastUsage >= GMAIL_CONNECTION_TIMEOUT;
    }

    private static final void disconnect(GmailEntry entry) {
        if (entry != null) {
            try {
                entry.disconnect();
            } catch (Exception ignored) {
            }
            log.debug("Gmail connection closed.");
        }
    }

    // --- STOP POOL ---
    public final void interrupt() {

        // Interrupt thread
        super.interrupt();

        // Disconnect all connections
        synchronized (this) {
            Iterator entries = pool.iterator();
            while (entries.hasNext()) {
                disconnect((GmailEntry) entries.next());
            }
            pool.clear();
        }
    }
}
