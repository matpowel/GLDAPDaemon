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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.directory.shared.asn1.codec.DecoderException;
import org.apache.directory.shared.ldap.codec.LdapConstants;
import org.apache.directory.shared.ldap.codec.LdapDecoder;
import org.apache.directory.shared.ldap.codec.LdapMessage;
import org.apache.directory.shared.ldap.codec.LdapMessageContainer;
import org.apache.directory.shared.ldap.codec.LdapResponse;
import org.apache.directory.shared.ldap.codec.LdapResult;
import org.apache.directory.shared.ldap.codec.bind.BindResponse;
import org.apache.directory.shared.ldap.codec.search.Filter;
import org.apache.directory.shared.ldap.codec.search.SearchRequest;
import org.apache.directory.shared.ldap.codec.search.SearchResultDone;
import org.apache.directory.shared.ldap.codec.search.SearchResultEntry;
import org.apache.directory.shared.ldap.codec.search.SubstringFilter;
import org.apache.directory.shared.ldap.name.LdapDN;
import org.gldapdaemon.core.FilterMask;
import org.gldapdaemon.core.GmailContact;
import org.gldapdaemon.core.StringUtils;

/**
 * LDAP server thread.
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
final class LDAPListener extends Thread {

    // --- CONSTANTS ---
    private static final String PLATFORM_ENCODING = Charset.defaultCharset().name();
    // --- LOGGER ---
    private static final Log log = LogFactory.getLog(LDAPListener.class);
    // --- READ BUFFER ---
    private static final ByteBuffer requestBuffer = ByteBuffer.allocateDirect(1024);
    // --- VARIABLES ---
    private final ContactLoader loader;
    private final ServerSocketChannel serverChannel;
    private final Selector selector;
    private final FilterMask[] hosts;
    private final FilterMask[] addresses;

    // --- CONSTRUCTOR ---
    LDAPListener(ContactLoader loader, FilterMask[] hosts, FilterMask[] addresses, int port) throws Exception {

        // Starting server
        log.info("LDAP server starting on port " + port + "...");

        // Store pointers
        this.loader = loader;
        this.hosts = hosts;
        this.addresses = addresses;

        // Allocate an unbound server socket channel
        serverChannel = ServerSocketChannel.open();

        // Get the associated ServerSocket to bind it with
        ServerSocket serverSocket = serverChannel.socket();

        // Set the port the server channel will listen to
        serverSocket.bind(new InetSocketAddress(port));

        // Set non-blocking mode for the listening socket
        serverChannel.configureBlocking(false);

        // Create a new Selector for use below
        selector = Selector.open();

        // Register the ServerSocketChannel with the Selector
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        // Start thread
        start();
    }

    // --- REQUEST LISTENER LOOP ---
    public final void run() {
        log.info("LDAP server started successfully.");

        // Create variables
        SelectionKey key, newKey;
        SocketChannel channel;
        Socket socket = null;
        Iterator keys;
        int n;

        // Server loop
        for (;;) {
            try {
                // Select sockets
                try {
                    socket = null;
                    n = selector.select();
                } catch (NullPointerException closedError) {
                    // Ignore Selector bug - client socket closed
                    if (log.isDebugEnabled()) {
                        log.debug("Socket closed.", closedError);
                    }
                    sleep(5000);
                    continue;
                } catch (ClosedSelectorException interrupt) {
                    break;
                } catch (Exception selectError) {
                    // Unknown exception - stop server
                    log.warn("Unable to select sockets!", selectError);
                    break;
                }

                if (n != 0) {
                    // Get an iterator over the set of selected keys
                    keys = selector.selectedKeys().iterator();
                    if (keys == null) {
                        sleep(5000);
                        continue;
                    }

                    // Look at each key in the selected set
                    while (keys.hasNext()) {
                        key = (SelectionKey) keys.next();
                        keys.remove();

                        // Nothing to do
                        if (key == null) {
                            sleep(5000);
                            continue;
                        }

                        // Check key status
                        if (key.isValid()) {
                            // Accept new incoming connection
                            if (key.isAcceptable()) {
                                channel = serverChannel.accept();
                                if (channel != null) {
                                    // Register new socket connection
                                    socket = channel.socket();
                                    channel.configureBlocking(false);
                                    newKey = channel.register(selector, SelectionKey.OP_READ);
                                    processAccept(newKey);
                                }
                            } else {
                                if (key.isReadable()) {
                                    // Read from socket connection
                                    socket = ((SocketChannel) key.channel()).socket();
                                    processRead(key);
                                } else {
                                    // Write to socket connection
                                    if (key.isWritable()) {
                                        socket = ((SocketChannel) key.channel()).socket();
                                        processWrite(key);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (InterruptedException interrupt) {
                closeSocket(socket);
                break;
            } catch (IOException socketClosed) {
                closeSocket(socket);
                continue;
            } catch (Exception processingException) {
                closeSocket(socket);
                log.warn(processingException.getMessage(), processingException);
            }
        }
        log.info("LDAP server stopped.");
    }

    private static final void closeSocket(Socket socket) {
        if (socket != null) {
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    // --- TCP/IP ACCESS CONTROL ---
    private final void processAccept(SelectionKey key) throws Exception {

        // Check TCP/IP access
        if (hosts != null || addresses != null) {
            Socket socket = ((SocketChannel) key.channel()).socket();
            InetAddress inetAddress = socket.getInetAddress();
            if (hosts != null) {
                String host = inetAddress.getHostName();
                if (host == null || host.length() == 0
                        || host.equals("127.0.0.1")) {
                    host = "localhost";
                } else {
                    host = host.toLowerCase();
                    if (host.equals("localhost.localdomain")) {
                        host = "localhost";
                    }
                }
                if (!isHostMatch(host)) {
                    throw new Exception(
                            "Connection refused, forbidden hostname (" + host
                            + ")!");
                }
            }
            if (addresses != null) {
                String address = inetAddress.getHostAddress();
                if (address == null || address.length() == 0) {
                    address = "127.0.0.1";
                }
                if (!isAddressMatch(address)) {
                    throw new Exception(
                            "Connection refused, forbidden IP-address ("
                            + address + ")!");
                }
            }
        }
    }

    private final boolean isAddressMatch(String string) {
        for (int i = 0; i < addresses.length; i++) {
            if (addresses[i].match(string)) {
                return true;
            }
        }
        return false;
    }

    private final boolean isHostMatch(String string) {
        for (int i = 0; i < hosts.length; i++) {
            if (hosts[i].match(string)) {
                return true;
            }
        }
        return false;
    }

    // --- LDAP REQUEST PROCESSOR ---
    private final void processRead(SelectionKey key) throws Exception {

        // Read packet from socket channel
        sleep(100);
        byte[] bytes = null;
        Object att = key.attachment();
        if (att != null && att instanceof byte[]) {
            bytes = (byte[]) att;
        }
        SocketChannel channel = (SocketChannel) key.channel();
        requestBuffer.clear();
        int len = channel.read(requestBuffer);
        if (len == -1) {
            throw new IOException();
        }
        if (len != 0) {
            requestBuffer.flip();
            byte[] packet = new byte[len];
            requestBuffer.get(packet, 0, len);
            if (bytes == null || bytes.length == 0) {
                bytes = packet;
                key.attach(bytes);
            } else {
                byte[] swap = new byte[bytes.length + packet.length];
                System.arraycopy(bytes, 0, swap, 0, bytes.length);
                System.arraycopy(packet, 0, swap, bytes.length, packet.length);
                bytes = swap;
                key.attach(bytes);
            }

            // Try to process packet
            LdapMessageContainer container = new LdapMessageContainer();
            try {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                LdapDecoder decoder = new LdapDecoder();
                decoder.decode(buffer, container);
            } catch (DecoderException emptyStringException) {
                String msg = emptyStringException.getMessage();
                if (msg != null && (msg.indexOf("empty") != -1 || msg.indexOf("transition") != -1)) {
                    // All contacts requested
                    int id = container.getMessageId();
                    SearchRequest search = new SearchRequest();
                    search.setMessageId(id);
                    LdapMessage ldap = new LdapMessage();
                    ldap.setMessageId(id);
                    ldap.setProtocolOP(search);
                    container.setLdapMessage(ldap);
                } else {
                    throw emptyStringException;
                }
            }

            // Process LDAP request
            ByteBuffer response = processRequest(container.getLdapMessage(), !container.isGrammarEndAllowed());
            key.attach(response);
            key.interestOps(SelectionKey.OP_WRITE);
        }
    }
    private boolean nativeCharsetLocked = false;

    private final ByteBuffer processRequest(LdapMessage request, boolean utf8)
            throws Exception {
        if (log.isDebugEnabled()) {
            try {
                String command = request.getMessageTypeName();
                if (command != null) {
                    command = command.toLowerCase().replace('_', ' ');
                }
                log.debug("Processing " + command + "...");
            } catch (Exception ignored) {
                log.warn("Processing unknown LDAP request...");
            }
        }
        LinkedList list = new LinkedList();
        switch (request.getMessageType()) {
            case LdapConstants.BIND_REQUEST:

                // Bind response
                BindResponse bind = new BindResponse();
                bind.setMessageId(request.getMessageId());
                LdapResult result = new LdapResult();
                result.setResultCode(0);
                bind.setLdapResult(result);
                list.addLast(bind);
                break;

            case LdapConstants.UNBIND_REQUEST:

                // Unbind response
                LdapResponse unbind = new LdapResponse();
                unbind.setMessageId(request.getMessageId());
                result = new LdapResult();
                result.setResultCode(0);
                unbind.setLdapResult(result);
                list.addLast(unbind);
                break;

            case LdapConstants.SEARCH_REQUEST:

                // Switch back encoding
                if (nativeCharsetLocked) {
                    utf8 = false;
                }

                // Get search string
                SearchRequest search = request.getSearchRequest();
                Filter filter = search.getTerminalFilter();
                String key = null;
                if (filter == null) {
                    filter = search.getFilter();
                    if (filter == null) {
                        filter = search.getCurrentFilter();
                    }
                }
                if (filter != null) {
                    if (filter instanceof SubstringFilter) {
                        SubstringFilter substringFilter = (SubstringFilter) filter;
                        ArrayList substrings = substringFilter.getAnySubstrings();
                        if (substrings != null && substrings.size() != 0) {
                            key = (String) substrings.get(0);
                        }
                    }
                    if (key == null) {
                        key = filter.toString();
                        if (key != null) {
                            if (key.charAt(0) == '*') {
                                key = key.substring(1);
                            }
                            if (key.charAt(key.length() - 1) == '*') {
                                key = key.substring(0, key.length() - 1);
                            }
                            if (key.indexOf('=') != -1) {
                                key = key.substring(key.indexOf('=') + 1);
                            }
                        }
                    }
                    if (key != null) {
                        if (key.length() == 0) {
                            key = null;
                        } else {

                            // Decode UTF8 chars
                            try {
                                byte[] bytes = key.getBytes(PLATFORM_ENCODING);
                                key = StringUtils.decodeToString(bytes, StringUtils.UTF_8);
                                if (utf8) {
                                    bytes = key.getBytes(PLATFORM_ENCODING);
                                    key = StringUtils.decodeToString(bytes, StringUtils.UTF_8);
                                }
                            } catch (Exception ignored) {
                            }

                            if (log.isDebugEnabled()) {
                                log.debug("LDAP search filter (" + key + ") received.");
                            }
                            key = key.toLowerCase();

                            // All contacts requested
                            if (key.equals("@")) {
                                key = null;
                            }
                        }
                    }
                }

                // Handle native charset lock
                if (key != null && !utf8) {
                    nativeCharsetLocked = true;
                }

                // Find entry
                ArrayList<GmailContact> contacts = loader.getContacts();
                if (contacts != null) {
                    GmailContact contact;
                    for (int n = 0; n < contacts.size(); n++) {
                        contact = contacts.get(n);
                        String value = null;
                        if (contact.name.toLowerCase().indexOf(key) >= 0 || contact.company.toLowerCase().indexOf(key) >= 0) {
                            value = contact.name.length() > 0 ? contact.name : contact.company;
                        } else if (key != null) {
                            continue;
                        }

                        // Add search entry
                        SearchResultEntry entry = new SearchResultEntry();
                        entry.setMessageId(request.getMessageId());
                        LdapDN name;
                        try {
                            name = new LdapDN("CN=" + encode(value, utf8));
                        } catch (Exception badDN) {
                            log.debug(badDN);
                            continue;
                        }
                        entry.setObjectName(name);

                        BasicAttributes partialAttributeList = new BasicAttributes(true);
                        partialAttributeList.put(new BasicAttribute("cn", encode(value, utf8)));
                        if (contact.email.length() != 0) {
                            // first email
                            partialAttributeList.put(new BasicAttribute("mail", encode(contact.email, utf8)));
                        }
                        if (contact.notes.length() != 0) {
                            // notes
                            partialAttributeList.put(new BasicAttribute("comment", encode(contact.notes, utf8)));
                            partialAttributeList.put(new BasicAttribute("description", encode(contact.notes, utf8)));
                        }
                        String mobile = contact.mobile;
                        if (mobile.length() == 0) {
                            mobile = contact.phone;
                        }
                        if (mobile.length() != 0) {
                            // mobile phone
                            partialAttributeList.put(new BasicAttribute("telephonenumber", encode(mobile, utf8)));
                        }
                        if (contact.phone.length() != 0) {

                            // homePhone
                            partialAttributeList.put(new BasicAttribute("homePhone", encode(contact.phone, utf8)));
                        }
                        if (contact.mail.length() != 0) {

                            // second email
                            partialAttributeList.put(new BasicAttribute("mozillaSecondEmail", encode(contact.mail, utf8)));
                            partialAttributeList.put(new BasicAttribute("mailAlternateAddress", encode(contact.mail, utf8)));
                        }
                        if (contact.address.length() != 0) {

                            // postal address
                            partialAttributeList.put(new BasicAttribute("postalAddress", encode(contact.address, utf8)));
                            partialAttributeList.put(new BasicAttribute("homePostalAddress", encode(contact.address, utf8)));
                            partialAttributeList.put(new BasicAttribute("homeStreet", encode(contact.address, utf8)));
                        }
                        if (contact.pager.length() != 0) {

                            // pager
                            partialAttributeList.put(new BasicAttribute("pager", encode(contact.pager, utf8)));
                        }
                        if (contact.fax.length() != 0) {

                            // fax
                            partialAttributeList.put(new BasicAttribute("facsimileTelephoneNumber", encode(contact.fax, utf8)));
                            if (contact.pager.length() == 0) {
                                partialAttributeList.put(new BasicAttribute("pager", encode(contact.fax, utf8)));
                            }
                        }
                        if (contact.title.length() != 0) {

                            // title
                            partialAttributeList.put(new BasicAttribute("title", encode(contact.title, utf8)));
                        }
                        if (contact.company.length() != 0) {

                            // company
                            partialAttributeList.put(new BasicAttribute("company", encode(contact.company, utf8)));
                            partialAttributeList.put(new BasicAttribute("o", encode(contact.company, utf8)));
                        }
                        entry.setPartialAttributeList(partialAttributeList);
                        list.addLast(entry);
                    }
                }

                // Search done
                if (log.isDebugEnabled()) {
                    log.debug("Found " + list.size() + " contacts.");
                }
                SearchResultDone done = new SearchResultDone();
                done.setMessageId(request.getMessageId());
                result = new LdapResult();
                result.setResultCode(0);
                done.setLdapResult(result);
                list.addLast(done);
                break;

            case LdapConstants.ABANDON_REQUEST:

                // Abandon command
                result = new LdapResult();
                result.setResultCode(0);
                LdapResponse response = new LdapResponse();
                response.setLdapResult(result);
                list.addLast(response);
                break;

            default:

                // Unsupported command
                log.debug("Unsupported LDAP command!");
                result = new LdapResult();
                result.setErrorMessage("Unsupported LDAP command!");
                response = new LdapResponse();
                response.setLdapResult(result);
                list.addLast(response);
        }
        log.debug("LDAP request processed.");
        if (!list.isEmpty()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Iterator responses = list.iterator();
            while (responses.hasNext()) {
                LdapMessage response = (LdapMessage) responses.next();
                response.setMessageId(request.getMessageId());

                // Append LDAP response
                LdapMessage message = new LdapMessage();
                message.setProtocolOP(response);
                message.setMessageId(request.getMessageId());
                ByteBuffer bb = message.encode(null);
                byte[] a = bb.array();
                out.write(a);
            }
            byte[] bytes = out.toByteArray();
            return ByteBuffer.wrap(bytes);
        }
        return null;
    }

    private static final String encode(String text, boolean utf8)
            throws Exception {
        if (utf8) {
            return new String(text.getBytes("UTF8"), PLATFORM_ENCODING);
        }
        return text;
    }

    private static final void processWrite(SelectionKey key) throws Exception {
        Object att = key.attachment();
        if (att == null) {
            Thread.sleep(100);
            return;
        }
        if (att instanceof ByteBuffer) {
            ByteBuffer buffer = (ByteBuffer) att;
            if (!buffer.hasRemaining()) {
                key.attach(new byte[0]);
                key.interestOps(SelectionKey.OP_READ);
                return;
            }
            SocketChannel channel = (SocketChannel) key.channel();
            channel.write(buffer);
        }
    }

    // --- STOP SERVICE ---
    public final void interrupt() {

        // Interrupt thread
        super.interrupt();

        // Close resources
        try {
            if (serverChannel.isOpen()) {
                serverChannel.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (selector.isOpen()) {
                selector.close();
            }
        } catch (Exception ignored) {
        }
    }
}
