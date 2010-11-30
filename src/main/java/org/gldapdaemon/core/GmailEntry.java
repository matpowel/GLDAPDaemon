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

import com.google.gdata.client.Query;
import com.google.gdata.client.contacts.ContactsService;
import com.google.gdata.data.contacts.ContactEntry;
import com.google.gdata.data.contacts.ContactFeed;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Pooled Gmail connection.
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class GmailEntry {

    // --- LDAP CONSTANTS ---
    private static final int HTTP_CONNECTION_TIMEOUT = 10000;
    private static final int HTTP_WAIT_TIMEOUT = 60000;
    private static final String USER_AGENT = "Mozilla/5.0 (Windows; U;" + " Windows NT 5.1; hu; rv:1.8.0.8) Gecko/20061025 Thunderbird/1.5.0.8";
    // --- LDAP CONNECTION VARIABLES ---
    private ContactsService contactsService;
    // --- PACKAGE-PRIVATE VARIABLES ---
    String username;
    long lastUsage;
    // --- LOGGER ---
    private static final Log log = LogFactory.getLog(GmailEntry.class);

    // --- CONSTRUCTOR ---
    private final boolean ldap;

    GmailEntry(boolean ldap) {
        this.ldap = ldap;
    }
    // --- CONNECT ALL SERVICES ---
    private boolean connected;

    final void connect(String username, String password) throws Exception {

        // Login to LDAP service
        if (ldap) {
            connectLDAP(username, password);
        }

        connected = true;
    }

    public final boolean isConnected() {
        return connected;
    }

    // --- LDAP CONNECT ---
    private final void connectLDAP(String username, String password)
            throws Exception {

        this.username = username;

        // Google Apps For Your Domain support
        contactsService = new ContactsService("agworld-GCALDaemon-1.01");
        contactsService.setUserCredentials(username, password);
    }

    // --- DISCONNECT ALL SERVICES ---
    final void disconnect() {
        // Disconnect LDAP service
        if (ldap) {
            contactsService = null;
        }
    }

    // --- CONTACT LOADER [LDAP SERVICE] ---
    public final List getContacts() throws Exception {

        log.info("Downloading all contacts for " + username);

        URL feedUrl = new URL("http://www.google.com/m8/feeds/contacts/" + username + "/full");
        //ContactFeed resultFeed = contactsService.getFeed(feedUrl, ContactFeed.class);
        Query allContactsQuery = new Query(feedUrl);
        allContactsQuery.setMaxResults( 1000000 ); // if the user has more than 1,000,000 contacts they deserve what they get :-)
        ContactFeed resultFeed = contactsService.query(allContactsQuery, ContactFeed.class);

        log.info("Downloaded "+resultFeed.getEntries().size()+" contacts");

        ArrayList contacts = new ArrayList();
        for (ContactEntry entry : resultFeed.getEntries()) {
            contacts.add(new GmailContact(entry));
        }
        return contacts;
    }
}
