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
package org.gldapdaemon.core.ldap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.codec.net.QuotedPrintableCodec;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gldapdaemon.core.Configurator;
import org.gldapdaemon.core.FilterMask;
import org.gldapdaemon.core.GmailContact;
import org.gldapdaemon.core.GmailEntry;
import org.gldapdaemon.core.GmailPool;
import org.gldapdaemon.core.StringUtils;
import org.gldapdaemon.logger.QuickWriter;

/**
 * Periodic Gmail contact loader thread.
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class ContactLoader extends Thread {

    // --- CONSTANTS ---
    private static final String NATIVE_CHARSET = Charset.defaultCharset().name();
    private static final String VCARD_EXTENSION = ".vcf";
    private static final byte VCARD_UTF8_ENCODING = 0;
    private static final byte VCARD_NATIVE_ENCODING = 1;
    private static final byte VCARD_QUOTED_ENCODING = 2;
    private static final int MAX_INDEX_GAP = 100;
    // --- LOGGER ---
    private static final Log log = LogFactory.getLog(ContactLoader.class);
    // --- VARIABLES ---
    private final Configurator configurator;
    private final LDAPListener ldapListener;
    private final long pollingTimeout;
    private final File vcardDirectory;
    private final byte vcardEncoding;
    private final String vcardVersion;
    private final String[] usernames;
    private final String[] passwords;
    private volatile ArrayList<GmailContact> contacts;

    // --- CONSTRUCTOR ---
    public ContactLoader(ThreadGroup mainGroup, Configurator configurator)
            throws Exception {
        super(mainGroup, "Contact loader");
        this.configurator = configurator;
        this.vcardDirectory = new File(configurator.getWorkDirectory(), "vcard");
        if (!vcardDirectory.isDirectory()) {
            vcardDirectory.mkdirs();
        }

        // Acceptable hostnames
        FilterMask[] hosts = configurator.getFilterProperty(Configurator.LDAP_ALLOWED_HOSTNAMES);

        // Acceptable TCP/IP addresses
        FilterMask[] addresses = configurator.getFilterProperty(Configurator.LDAP_ALLOWED_ADDRESSES);

        // Get contact list cache timeout
        long timeout = configurator.getConfigProperty(Configurator.LDAP_CACHE_TIMEOUT, 3600000L);
        if (timeout < 180000L) {
            log.warn("The fastest contact list polling period is '3 min'!");
            timeout = 180000L;
        }
        pollingTimeout = timeout;

        // Get username/password pairs
        LinkedList usernameList = new LinkedList();
        LinkedList passwordList = new LinkedList();
        String parameterPostfix;
        int gapCounter = 0;
        for (int i = 1;; i++) {

            // Create parameter postfix [..n]
            if (i == 1) {
                parameterPostfix = "";
            } else {
                parameterPostfix = Integer.toString(i);
            }
            if (configurator.getConfigProperty(
                    Configurator.LDAP_GOOGLE_USERNAME + parameterPostfix, null) == null) {
                if (gapCounter < MAX_INDEX_GAP) {
                    gapCounter++;
                    continue;
                }
                break;
            }
            gapCounter = 0;

            // Get username
            String username = configurator.getConfigProperty(
                    Configurator.LDAP_GOOGLE_USERNAME + parameterPostfix, null);

            // Get password
            String password = null;
            if (configurator.getConfigProperty(
                    Configurator.LDAP_GOOGLE_PASSWORD + parameterPostfix, null) != null) {
                password = configurator.getPasswordProperty(Configurator.LDAP_GOOGLE_PASSWORD
                        + parameterPostfix);
            }

            // Verify parameters
            if (username == null) {
                throw new NullPointerException("Missing username ("
                        + Configurator.LDAP_GOOGLE_USERNAME + parameterPostfix
                        + ")!");
            }
            if (password == null) {
                throw new NullPointerException("Missing password ("
                        + Configurator.LDAP_GOOGLE_PASSWORD + parameterPostfix
                        + ")!");
            }

            // Add parameters to lists
            usernameList.addLast(username);
            passwordList.addLast(password);
        }

        // Create object arrays
        usernames = new String[usernameList.size()];
        passwords = new String[passwordList.size()];
        usernameList.toArray(usernames);
        passwordList.toArray(passwords);

        if (hosts == null && addresses == null) {
            // Security warning
            log.warn("Set the '" + Configurator.LDAP_ALLOWED_HOSTNAMES + "' parameter to limit remote access.");
        } else {

            // Debug filters
            if (log.isDebugEnabled()) {
                log.debug("Allowed LDAP hosts: " + configurator.getConfigProperty(Configurator.LDAP_ALLOWED_HOSTNAMES, "*"));
                log.debug("Allowed LDAP addresses: " + configurator.getConfigProperty(Configurator.LDAP_ALLOWED_ADDRESSES, "*"));
            }
        }

        // Get vCard properties
        String value = configurator.getConfigProperty(Configurator.LDAP_VCARD_ENCODING, "quoted");
        if (value.equals("quoted")) {
            vcardEncoding = VCARD_QUOTED_ENCODING;
        } else {
            if (value.equals("native")) {
                vcardEncoding = VCARD_NATIVE_ENCODING;
            } else {
                vcardEncoding = VCARD_UTF8_ENCODING;
            }
        }
        value = configurator.getConfigProperty(Configurator.LDAP_VCARD_VERSION, "3.0");
        try {
            double num = Double.parseDouble(value);
            vcardVersion = Double.toString(num);
        } catch (Exception formatError) {
            log.fatal("Invalid vCard version: " + value);
            throw formatError;
        }

        // Create and start LDAP listener
        int port = (int) configurator.getConfigProperty(Configurator.LDAP_PORT,
                9080);
        ldapListener = new LDAPListener(this, hosts, addresses, port);

        // Start listener
        start();
    }

    // --- CONTACT LOADER LOOP ---
    public final void run() {
        for (;;) {
            try {

                // Load contact list
                for (int tries = 0;; tries++) {
                    try {
                        loadContacts();
                        break;
                    } catch (IOException networkError) {
                        if (tries == 5) {
                            throw networkError;
                        }
                        log.debug("Connection refused, reconnecting...");
                        Thread.sleep(500);
                    } catch (Exception genericError) {
                        throw genericError;
                    }
                }

                // Wait
                sleep(pollingTimeout);

            } catch (InterruptedException interrupt) {
                // Service stopped
                return;
            } catch (Exception loadError) {
                try {
                    log.error("Unable to load contact list!", loadError);
                    sleep(pollingTimeout);
                } catch (Exception ex) {
                    return;
                }
            }
        }
    }

    // --- CONTACT LIST LOADER ---
    private final void loadContacts() throws Exception {

        // Loading contact list
        log.debug("Loading Gmail contact list...");
        GmailPool pool = configurator.getGmailPool();
        HashSet cardFiles = new HashSet();
        GmailEntry entry = null;

        // Loop on accounts
        ArrayList allContacts = new ArrayList();
        for (int n = 0; n < usernames.length; n++) {
            try {
                // Download CSV from Gmail
                entry = pool.borrow(usernames[n], passwords[n]);
                allContacts.addAll(entry.getContacts());
            } finally {
                pool.recycle(entry);
            }
            if (n < usernames.length - 1) {
                Thread.sleep(1000);
            }
        }

        // Save contact in other formats (eg. HTML)
        saveContacts(vcardDirectory, allContacts);

        // Remove deleted contacts
        String[] currentFiles = vcardDirectory.list();
        String fileName;
        for (int i = 0; i < currentFiles.length; i++) {
            fileName = currentFiles[i];
            if (fileName.endsWith(VCARD_EXTENSION) && !cardFiles.contains(fileName)) {
                (new File(vcardDirectory, fileName)).delete();
            }
        }

        // Contact list loaded
        synchronized (this) {
            contacts = allContacts;
        }
        log.debug(allContacts.size() + " contacts loaded successfully.");
    }

    private static final void saveContacts(File vcardDirectory, List<GmailContact> contacts) throws Exception {
        QuickWriter buffer = new QuickWriter();
        GmailContact contact;
        byte[] bytes;
        File file;
        int i;

        // Persist a seralized version of the contacts as fall back for offline
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        file = new File(vcardDirectory, "contacts.bin");
        try {
            fos = new FileOutputStream(file);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(contacts);
        } finally {
            if (oos != null) {
                oos.close();
            }
            if (fos != null) {
                fos.close();
            }
        }

        // Save HTML
        buffer.flush();
        buffer.write("<html>\r\n");
        buffer.write("<head>\r\n");
        buffer.write("<title>Contacts</title>\r\n");
        buffer.write("<meta http-equiv=\"content-type\" ");
        buffer.write("content=\"text/html; charset=UTF-8\"/>\r\n");
        buffer.write("<style type=\"text/css\">\r\n");
        buffer.write("td {font-size: 11px; font-family: Arial,Helvetica;}\r\n");
        buffer.write("th {font-size: 11px; font-family: Arial,Helvetica;}\r\n");
        buffer.write("</style>\r\n");
        buffer.write("</head>\r\n");
        buffer.write("<body>\r\n");
        buffer.write("<table border=\"0\" cellspacing=\"0\" ");
        buffer.write("cellpadding=\"5\">\r\n");

        buffer.write("<tr bgcolor=\"lightgray\">");
        buffer.write("<th>NAME</th>");
        buffer.write("<th>MAIL</th>");
        buffer.write("<th>NOTES</th>");
        buffer.write("<th>DESCR</th>");
        buffer.write("<th>MAIL2</th>");
        buffer.write("<th>IM</th>");
        buffer.write("<th>PHONE</th>");
        buffer.write("<th>MOBILE</th>");
        buffer.write("<th>PAGER</th>");
        buffer.write("<th>FAX</th>");
        buffer.write("<th>COMPANY</th>");
        buffer.write("<th>TITLE</th>");
        buffer.write("<th>OTHER</th>");
        buffer.write("<th>ADDRESS</th>");
        buffer.write("</tr>\r\n");

        for (i = 0; i < contacts.size(); i++) {
            contact = contacts.get(i);
            if (i % 2 != 1) {
                buffer.write("<tr>");
            } else {
                buffer.write("<tr bgcolor=\"lightgray\">");
            }

            buffer.write("<td>");
            buffer.write(contact.name);
            buffer.write("&nbsp;</td>");

            buffer.write("<td>");
            buffer.write(contact.email.replace(",", ", "));
            buffer.write("&nbsp;</td>");

            buffer.write("<td>");
            buffer.write(contact.notes);
            buffer.write("&nbsp;</td>");

            buffer.write("<td>");
            buffer.write(contact.description);
            buffer.write("&nbsp;</td>");

            buffer.write("<td>");
            buffer.write(contact.mail);
            buffer.write("&nbsp;</td>");

            buffer.write("<td>");
            buffer.write(contact.im);
            buffer.write("&nbsp;</td>");

            buffer.write("<td>");
            buffer.write(contact.phone);
            buffer.write("&nbsp;</td>");

            buffer.write("<td>");
            buffer.write(contact.mobile);
            buffer.write("&nbsp;</td>");

            buffer.write("<td>");
            buffer.write(contact.pager);
            buffer.write("&nbsp;</td>");

            buffer.write("<td>");
            buffer.write(contact.fax);
            buffer.write("&nbsp;</td>");

            buffer.write("<td>");
            buffer.write(contact.company);
            buffer.write("&nbsp;</td>");

            buffer.write("<td>");
            buffer.write(contact.title);
            buffer.write("&nbsp;</td>");

            buffer.write("<td>");
            buffer.write(contact.other);
            buffer.write("&nbsp;</td>");

            buffer.write("<td>");
            buffer.write(contact.address);
            buffer.write("&nbsp;</td>");

            buffer.write("</tr>\r\n");
        }
        buffer.write("</table>\r\n");
        buffer.write("</body>\r\n");
        buffer.write("</html>");
        file = new File(vcardDirectory, "contacts.html");
        bytes = StringUtils.encodeString(buffer.toString(), StringUtils.UTF_8);
        saveFile(file, bytes);

        // Save XML
        buffer.flush();
        buffer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n");
        buffer.write("<address-book>\r\n");

        for (i = 0; i < contacts.size(); i++) {
            contact = contacts.get(i);
            buffer.write("\t<contact>\r\n");

            buffer.write("\t\t<name>");
            buffer.write(contact.name);
            buffer.write("</name>\r\n");

            buffer.write("\t\t<email>");
            buffer.write(contact.email.replace(",", ", "));
            buffer.write("</email>\r\n");

            buffer.write("\t\t<notes>");
            buffer.write(contact.notes);
            buffer.write("</notes>\r\n");

            buffer.write("\t\t<description>");
            buffer.write(contact.description);
            buffer.write("</description>\r\n");

            buffer.write("\t\t<mail>");
            buffer.write(contact.mail);
            buffer.write("</mail>\r\n");

            buffer.write("\t\t<im>");
            buffer.write(contact.im);
            buffer.write("</im>\r\n");

            buffer.write("\t\t<phone>");
            buffer.write(contact.phone);
            buffer.write("</phone>\r\n");

            buffer.write("\t\t<mobile>");
            buffer.write(contact.mobile);
            buffer.write("</mobile>\r\n");

            buffer.write("\t\t<pager>");
            buffer.write(contact.pager);
            buffer.write("</pager>\r\n");

            buffer.write("\t\t<fax>");
            buffer.write(contact.fax);
            buffer.write("</fax>\r\n");

            buffer.write("\t\t<company>");
            buffer.write(contact.company);
            buffer.write("</company>\r\n");

            buffer.write("\t\t<title>");
            buffer.write(contact.title);
            buffer.write("</title>\r\n");

            buffer.write("\t\t<other>");
            buffer.write(contact.other);
            buffer.write("</other>\r\n");

            buffer.write("\t\t<address>");
            buffer.write(contact.address);
            buffer.write("</address>\r\n");

            buffer.write("\t</contact>\r\n");
        }
        buffer.write("</address-book>");
        file = new File(vcardDirectory, "contacts.xml");
        bytes = StringUtils.encodeString(buffer.toString(), StringUtils.UTF_8);
        saveFile(file, bytes);

    }

    private static final void saveFile(File file, byte[] bytes) throws Exception {
        FileOutputStream out = null;
        for (int retries = 0;; retries++) {
            try {
                out = new FileOutputStream(file);
                out.write(bytes);
                out.flush();
                out.close();
                out = null;
                break;
            } catch (Exception lockedError) {
                if (out != null) {
                    try {
                        out.close();
                    } catch (Exception ignored) {
                    }
                    out = null;
                }
                if (retries == 5) {
                    throw lockedError;
                }
                Thread.sleep(500);
            }
        }
    }

    private final String saveVCard(GmailContact contact, String rev) {
        String name = contact.email.toLowerCase();
        if (name.length() == 0) {
            name = contact.name.toLowerCase();
        }
        name = name.trim();
        if (name.length() == 0 || name.indexOf('=') != -1) {
            return VCARD_EXTENSION;
        }
        char[] chars = name.toCharArray();
        QuickWriter writer = new QuickWriter(chars.length);
        boolean writeMinus = true;
        char c;
        int i;
        for (i = 0; i < chars.length; i++) {
            c = chars[i];
            if (c != '_' && Character.isJavaIdentifierPart(c)) {
                writer.write(c);
                writeMinus = true;
                continue;
            }
            if (c == ',') {
                break;
            }
            if (writeMinus) {
                writer.write('-');
                writeMinus = false;
            }
        }
        name = writer.toString() + VCARD_EXTENSION;
        File file = new File(vcardDirectory, name);
        FileOutputStream out = null;
        try {
            writer = new QuickWriter(500);
            String encoding = StringUtils.UTF_8;
            String displayName = contact.name;
            if (displayName.length() == 0) {
                return VCARD_EXTENSION;
            }
            String firstName = null;
            String lastName = null;
            i = displayName.indexOf(' ');
            if (i != -1) {
                firstName = displayName.substring(0, i);
                lastName = displayName.substring(i + 1);
            }

            // Write vCard
            writer.write("BEGIN:VCARD\r\n");
            if (vcardVersion.charAt(0) == '3') {
                writer.write("VERSION:");
                writer.write(vcardVersion);
                writer.write("\r\nPRODID:");
                writer.write(Configurator.VERSION);
                writer.write("\r\n");
            } else {
                writer.write("VERSION:2.1\r\n");
            }
            switch (vcardEncoding) {
                case VCARD_UTF8_ENCODING:

                    // Pure UTF8 vCard format
                    writer.write("X-LOTUS-CHARSET:UTF-8\r\n");
                    writer.write("FN;CHARSET=UTF-8:");
                    writer.write(displayName);
                    if (firstName != null) {

                        // Name
                        writer.write("\r\nN;CHARSET=UTF-8:");
                        writer.write(firstName);
                        writer.write(';');
                        writer.write(lastName);
                        writer.write(";;;");
                    }
                    if (contact.notes.length() != 0) {

                        // Notes
                        writer.write("\r\nNOTE;CHARSET=UTF-8:");
                        writer.write(contact.notes);
                    }
                    if (contact.address.length() != 0) {

                        // Address
                        if (vcardVersion.charAt(0) == '3') {
                            writer.write("\r\nADR;TYPE=HOME;CHARSET=UTF-8:");
                        } else {
                            writer.write("\r\nADR;HOME;CHARSET=UTF-8:");
                        }
                        writer.write(contact.address);
                    }
                    break;
                case VCARD_NATIVE_ENCODING:

                    // Native vCard format
                    encoding = NATIVE_CHARSET;
                    writer.write("X-LOTUS-CHARSET:");
                    writer.write(NATIVE_CHARSET);
                    writer.write("\r\nFN:");
                    writer.write(displayName);
                    i = displayName.indexOf(' ');
                    if (firstName != null) {

                        // Name
                        writer.write("\r\nN:");
                        writer.write(firstName);
                        writer.write(';');
                        writer.write(lastName);
                    }
                    if (contact.notes.length() != 0) {

                        // Notes
                        writer.write("\r\nNOTE:");
                        writer.write(contact.notes);
                    }
                    if (contact.address.length() != 0) {

                        // Address
                        if (vcardVersion.charAt(0) == '3') {
                            writer.write("\r\nADR;TYPE=HOME:");
                        } else {
                            writer.write("\r\nADR;HOME:");
                        }
                        writer.write(contact.address);
                    }
                    break;
                default:

                    // Quoted-printable vCard format
                    encoding = StringUtils.US_ASCII;
                    writer.write("X-LOTUS-CHARSET:UTF-8\r\n");
                    writer.write("FN;QUOTED-PRINTABLE:");
                    writer.write(encodeQuotedPrintable(displayName));
                    i = displayName.indexOf(' ');
                    if (firstName != null) {

                        // Name
                        writer.write("\r\nN;QUOTED-PRINTABLE:");
                        writer.write(encodeQuotedPrintable(firstName));
                        writer.write(';');
                        writer.write(encodeQuotedPrintable(lastName));
                    }
                    if (contact.notes.length() != 0) {

                        // Notes
                        writer.write("\r\nNOTE;QUOTED-PRINTABLE:");
                        writer.write(encodeQuotedPrintable(contact.notes));
                    }
                    if (contact.address.length() != 0) {

                        // Address
                        if (vcardVersion.charAt(0) == '3') {
                            writer.write("\r\nADR;TYPE=HOME;QUOTED-PRINTABLE:");
                        } else {
                            writer.write("\r\nADR;HOME;QUOTED-PRINTABLE:");
                        }
                        writer.write(encodeQuotedPrintable(contact.address));
                    }
            }
            if (contact.email.length() != 0) {

                // Default email
                if (vcardVersion.charAt(0) == '3') {
                    writer.write("\r\nEMAIL;TYPE=PREF;TYPE=INTERNET:");
                } else {
                    writer.write("\r\nEMAIL;PREF;INTERNET:");
                }
                writer.write(contact.email);
            }
            if (contact.mail.length() != 0) {

                // Additional email
                if (vcardVersion.charAt(0) == '3') {
                    writer.write("\r\nEMAIL;TYPE=INTERNET:");
                } else {
                    writer.write("\r\nEMAIL;INTERNET:");
                }
                writer.write(contact.mail);
            }
            if (contact.phone.length() != 0) {

                // Phone number
                if (vcardVersion.charAt(0) == '3') {
                    writer.write("\r\nTEL;TYPE=HOME:");
                } else {
                    writer.write("\r\nTEL;HOME:");
                }
                writer.write(contact.phone);
            }
            writer.write("\r\nREV:");
            writer.write(rev);
            writer.write("\r\nEND:VCARD\r\n");
            byte[] bytes;
            if (encoding.equals(StringUtils.US_ASCII)) {
                bytes = writer.getBytes();
            } else {
                bytes = StringUtils.encodeString(writer.toString(), encoding);
            }
            for (int retries = 0;; retries++) {
                try {
                    out = new FileOutputStream(file);
                    out.write(bytes);
                    out.flush();
                    out.close();
                    out = null;
                    break;
                } catch (Exception lockedError) {
                    if (out != null) {
                        try {
                            out.close();
                        } catch (Exception ignored) {
                        }
                        out = null;
                    }
                    if (retries == 5) {
                        throw lockedError;
                    }
                    Thread.sleep(500);
                }
            }
        } catch (Exception ioError) {
            log.warn(ioError);
            if (file != null) {
                if (out != null) {
                    try {
                        out.close();
                    } catch (Exception ignored) {
                    }
                }
                file.delete();
            }
        }
        return name;
    }

    private static final String encodeQuotedPrintable(String string) throws Exception {
        byte[] bytes = StringUtils.encodeString(string, StringUtils.UTF_8);
        bytes = QuotedPrintableCodec.encodeQuotedPrintable(null, bytes);
        return StringUtils.decodeToString(bytes, StringUtils.US_ASCII);
    }

    private static final int parsePlainValue(String line, StringBuffer buffer, int offset) {
        // Parse the next plain value (e.g. Tom, xy@foo.com, etc)
        int nextOffset = line.indexOf(',', offset);
        if (nextOffset == -1) {
            buffer.append(line.substring(offset));
            return line.length();
        }
        buffer.append(line.substring(offset, nextOffset));
        return nextOffset;
    }

    private static final int parseSeparatedValue(String line,
            StringBuffer buffer, int offset) {
        int nextOffset;
        int len = line.length();

        // Loop on the quoted value (e.g. "xy@foo.com")
        for (nextOffset = offset; nextOffset < len; nextOffset++) {
            if (line.charAt(nextOffset) == '"' && nextOffset + 1 < len) {
                if (line.charAt(nextOffset + 1) == '"') {
                    nextOffset++;
                } else if (line.charAt(nextOffset + 1) == ',') {
                    nextOffset++;
                    break;
                }
            } else {
                if (line.charAt(nextOffset) == '"' && nextOffset + 1 == len) {
                    break;
                }
            }
            buffer.append(line.charAt(nextOffset));
        }
        return nextOffset;
    }

    // --- STOP SERVICE ---
    public final void interrupt() {

        // Close server socket and stop listener
        if (ldapListener != null) {
            try {
                ldapListener.interrupt();
            } catch (Exception closeError) {
                log.debug(closeError);
            }
        }

        // Interrupt thread
        super.interrupt();
    }

    // --- GMAIL CONTACT GETTER ---
    public final synchronized ArrayList<GmailContact> getContacts() {
        if (contacts == null) {
            try {
                log.warn("Network down - load contacts from serialization");
                File file = new File(vcardDirectory, "contacts.bin");
                if (!file.isFile()) {
                    return new ArrayList<GmailContact>();
                }
                FileInputStream fis = null;
                ObjectInputStream ois = null;
                try {
                    fis = new FileInputStream(file);
                    ois = new ObjectInputStream(fis);
                    contacts = (ArrayList<GmailContact>) ois.readObject();
                    log.info(contacts.size() + " contacts loaded successfully.");
                } catch (Exception ex) {
                    log.error("error loading cached contacts lists:\n" + ex.toString());
                } finally {
                    if (ois != null) {
                        ois.close();
                    }
                    if (fis != null) {
                        fis.close();
                    }
                }
            } catch (Exception ioError) {
                log.warn(ioError);
            }
        }
        return contacts;
    }
}
