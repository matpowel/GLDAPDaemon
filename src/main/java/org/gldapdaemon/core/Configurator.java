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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Properties;
import java.util.TimeZone;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.SimpleLayout;
import org.gldapdaemon.core.ldap.ContactLoader;

/**
 * Config loader, property setter, and listener starter object.
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class Configurator {

    // --- COMMON CONSTANTS ---
    public static final String VERSION = "GCALDaemon V1.0 beta 16";
    public static final byte MODE_DAEMON = 0;
    public static final byte MODE_RUNONCE = 1;
    public static final byte MODE_CONFIGEDITOR = 2;
    public static final byte MODE_EMBEDDED = 3;
    private static final int MAX_CACHE_SIZE = 100;
    private static final SimpleDateFormat BACKUP_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    // --- SIMPLE CONFIG CONSTANTS ---
    public static final String REMOTE_DELETE_ENABLED = "remote.delete.enabled";
    public static final String FILE_POLLING_FILE = "file.polling.file";
    public static final String FILE_RELOADER_SCRIPT = "file.reloader.script";
    public static final String LDAP_VCARD_ENCODING = "ldap.vcard.encoding";
    public static final String MAILTERM_DIR_PATH = "mailterm.dir.path";
    public static final String FEED_CACHE_TIMEOUT = "feed.cache.timeout";
    public static final String FILE_ENABLED = "file.enabled";
    public static final String NOTIFIER_LOCAL_USERS = "notifier.local.users";
    public static final String HTTP_ALLOWED_ADDRESSES = "http.allowed.addresses";
    public static final String PROXY_PASSWORD = "proxy.password";
    public static final String PROXY_USERNAME = "proxy.username";
    public static final String HTTP_ENABLED = "http.enabled";
    public static final String SENDMAIL_ENABLED = "sendmail.enabled";
    public static final String LDAP_GOOGLE_PASSWORD = "ldap.google.password";
    public static final String LDAP_GOOGLE_USERNAME = "ldap.google.username";
    public static final String NOTIFIER_WINDOW_SOUND = "notifier.window.sound";
    public static final String ICAL_BACKUP_TIMEOUT = "ical.backup.timeout";
    public static final String SEND_INVITATIONS = "send.invitations";
    public static final String MAILTERM_GOOGLE_PASSWORD = "mailterm.google.password";
    public static final String MAILTERM_GOOGLE_USERNAME = "mailterm.google.username";
    public static final String LDAP_CACHE_TIMEOUT = "ldap.cache.timeout";
    public static final String MAILTERM_ALLOWED_ADDRESSES = "mailterm.allowed.addresses";
    public static final String PROXY_PORT = "proxy.port";
    public static final String FEED_DUPLICATION_FILTER = "feed.duplication.filter";
    public static final String FEED_ENABLED = "feed.enabled";
    public static final String LDAP_ALLOWED_HOSTNAMES = "ldap.allowed.hostnames";
    public static final String SENDMAIL_GOOGLE_PASSWORD = "sendmail.google.password";
    public static final String SENDMAIL_GOOGLE_USERNAME = "sendmail.google.username";
    public static final String LDAP_ENABLED = "ldap.enabled";
    public static final String MAILTERM_CONSOLE_ENCODING = "mailterm.console.encoding";
    public static final String NOTIFIER_WINDOW_STYLE = "notifier.window.style";
    public static final String LDAP_VCARD_VERSION = "ldap.vcard.version";
    public static final String SENDMAIL_DIR_PATH = "sendmail.dir.path";
    public static final String LOG_CONFIG = "log.config";
    public static final String HTTP_PORT = "http.port";
    public static final String MAILTERM_POLLING_GOOGLE = "mailterm.polling.google";
    public static final String LDAP_ALLOWED_ADDRESSES = "ldap.allowed.addresses";
    public static final String PROGRESS_ENABLED = "progress.enabled";
    public static final String MAILTERM_MAIL_SUBJECT = "mailterm.mail.subject";
    public static final String SENDMAIL_POLLING_DIR = "sendmail.polling.dir";
    public static final String FEED_EVENT_LENGTH = "feed.event.length";
    public static final String PROXY_HOST = "proxy.host";
    public static final String NOTIFIER_DATE_FORMAT = "notifier.date.format";
    public static final String EXTENDED_SYNC_ENABLED = "extended.sync.enabled";
    public static final String HTTP_ALLOWED_HOSTNAMES = "http.allowed.hostnames";
    public static final String FILE_OFFLINE_ENABLED = "file.offline.enabled";
    public static final String MAILTERM_ENABLED = "mailterm.enabled";
    public static final String NOTIFIER_GOOGLE_PASSWORD = "notifier.google.password";
    public static final String NOTIFIER_POLLING_MAILBOX = "notifier.polling.mailbox";
    public static final String NOTIFIER_GOOGLE_USERNAME = "notifier.google.username";
    public static final String NOTIFIER_ENABLED = "notifier.enabled";
    public static final String CACHE_TIMEOUT = "cache.timeout";
    public static final String FILE_POLLING_GOOGLE = "file.polling.google";
    public static final String LDAP_PORT = "ldap.port";
    public static final String EDITOR_LANGUAGE = "editor.language";
    public static final String EDITOR_LOOK_AND_FEEL = "editor.look.and.feel";
    public static final String WORK_DIR = "work.dir";
    public static final String REMOTE_ALARM_TYPES = "remote.alarm.types";
    // --- FILE CONFIG CONSTANTS ---
    public static final String FILE_PRIVATE_ICAL_URL = "file.private.ical.url";
    public static final String FILE_ICAL_PATH = "file.ical.path";
    public static final String FILE_GOOGLE_USERNAME = "file.google.username";
    public static final String FILE_GOOGLE_PASSWORD = "file.google.password";
    // --- UTILS ---
    private Properties config = new Properties();
    private final File workDirectory;
    private final boolean standaloneMode;
    private final byte mode;
    private File configFile;
    // --- SERVICES AND LISTENERS ---
    private Thread gmailPool;
    private Thread contactLoader;
    // --- FEED CONVERTER'S PARAMETERS ---
    protected final double duplicationRatio;

    // --- CONSTRUCTOR ---
    public Configurator(String configPath, Properties properties, boolean userHome, byte mode) throws Exception {
        this.mode = mode;
        int i;
        File programRootDir = null;
        if (mode == MODE_EMBEDDED) {

            // Embedded mode
            standaloneMode = false;
            config = properties;
            String workPath = getConfigProperty(WORK_DIR, null);
            workDirectory = new File(workPath);
        } else {

            // Load config
            if (configPath != null) {
                configFile = new File(configPath);
            }
            InputStream in = null;
            boolean configInClassPath = false;
            if (configFile == null || !configFile.isFile()) {
                try {
                    in = Configurator.class.getResourceAsStream("/gcal-daemon.cfg");
                    configInClassPath = in != null;
                } catch (Exception ignored) {
                    in = null;
                }
                if (in == null) {
                    System.out.println("INFO  | Searching main configuration file...");
                    String path = (new File("x")).getAbsolutePath().replace(
                            '\\', '/');
                    i = path.lastIndexOf('/');
                    if (i > 1) {
                        i = path.lastIndexOf('/', i - 1);
                        if (i > 1) {
                            configFile = new File(path.substring(0, i),
                                    "conf/gcal-daemon.cfg");
                        }
                    }
                    if (configFile == null || !configFile.isFile()) {
                        configFile = new File("/usr/local/sbin/GCALDaemon/conf/gcal-daemon.cfg");
                    }
                    if (configFile == null || !configFile.isFile()) {
                        configFile = new File("/GCALDaemon/conf/gcal-daemon.cfg");
                    }
                    if (configFile == null || !configFile.isFile()) {
                        File root = new File("/");
                        String[] dirs = root.list();
                        if (dirs != null) {
                            for (i = 0; i < dirs.length; i++) {
                                configFile = new File('/' + dirs[i] + "/GCALDaemon/conf/gcal-daemon.cfg");
                                if (configFile.isFile()) {
                                    break;
                                }
                            }
                        }
                    }
                    if (configFile == null || !configFile.isFile()) {
                        throw new FileNotFoundException("Missing main configuration file: " + configPath);
                    }
                    if (!userHome) {

                        // Open global config file
                        in = new FileInputStream(configFile);
                    }
                }
            } else {
                if (!userHome) {
                    // Open global config file
                    in = new FileInputStream(configFile);
                }
            }
            standaloneMode = !configInClassPath;
            if (in != null) {

                // Load global config file
                config.load(new BufferedInputStream(in));
                in.close();
            }

            // Loading config from classpath
            if (configFile == null) {
                try {
                    URL url = Configurator.class.getResource("/gcal-daemon.cfg");
                    configFile = new File(url.getFile());
                } catch (Exception ignored) {
                }
            }
            programRootDir = configFile.getParentFile().getParentFile();
            System.setProperty("gldapdaemon.program.dir", programRootDir.getAbsolutePath());
            String workPath = getConfigProperty(WORK_DIR, null);
            File directory;
            if (workPath == null) {
                directory = new File(programRootDir, "work");
            } else {
                directory = new File(workPath);
            }
            if (!directory.isDirectory()) {
                if (!directory.mkdirs()) {
                    directory = new File("work");
                    directory.mkdirs();
                }
            }
            workDirectory = directory;

            // User-specific config file handler
            if (userHome) {
                boolean useGlobal = true;
                try {
                    String home = System.getProperty("user.home", null);
                    if (home != null) {
                        File userConfig = new File(home, ".gcaldaemon/gcal-daemon.cfg");
                        if (!userConfig.isFile()) {

                            // Create new user-specific config
                            File userDir = new File(home, ".gcaldaemon");
                            userDir.mkdirs();
                            copyFile(configFile, userConfig);
                            if (!userConfig.isFile()) {
                                userConfig.delete();
                                userDir.delete();
                            }
                        }
                        if (userConfig.isFile()) {

                            // Load user-specific config
                            configFile = userConfig;
                            in = new FileInputStream(configFile);
                            config.load(new BufferedInputStream(in));
                            in.close();
                            useGlobal = false;
                        }
                    }
                } catch (Exception ignored) {
                }
                if (useGlobal) {

                    // Load global config file
                    config.load(new BufferedInputStream(in));
                    in.close();
                }
            }
        }

        // Init logger
        ProgressMonitor monitor = null;
        if (standaloneMode && mode != MODE_CONFIGEDITOR) {

            // Compute log config path
            String logConfig = getConfigProperty(LOG_CONFIG, "logger-config.cfg");
            logConfig = logConfig.replace('\\', '/');
            File logConfigFile;
            if (logConfig.indexOf('/') == -1) {
                logConfigFile = new File(programRootDir, "conf/" + logConfig);
            } else {
                logConfigFile = new File(logConfig);
            }
            if (logConfigFile.isFile()) {
                String logConfigPath = logConfigFile.getAbsolutePath();
                System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.Log4JLogger");
                System.setProperty("log4j.defaultInitOverride", "false");
                System.setProperty("log4j.configuration", logConfigPath);
                try {
                    PropertyConfigurator.configure(logConfigPath);
                } catch (Throwable ignored) {
                    ignored.printStackTrace();
                }
            }
        }
        if (mode == MODE_CONFIGEDITOR) {

            // Show monitor
            try {
                monitor = new ProgressMonitor();
                monitor.setVisible(true);
                Thread.sleep(400);
            } catch (Exception ignored) {
            }

            // Init simple logger
            try {
                System.setProperty("log4j.defaultInitOverride", "false");
                Logger root = Logger.getRootLogger();
                root.removeAllAppenders();
                root.addAppender(new ConsoleAppender(new SimpleLayout()));
                root.setLevel(Level.INFO);
            } catch (Throwable ingored) {
            }
        }

        // Disable unnecessary INFO messages of the GData API
        try {
            java.util.logging.Logger logger = java.util.logging.Logger.getLogger("com.google");
            logger.setLevel(java.util.logging.Level.WARNING);
        } catch (Throwable ingored) {
        }

        Log log = LogFactory.getLog(Configurator.class);
        log.info(VERSION + " starting...");
        if (configFile != null && log.isDebugEnabled()) {
            log.debug("Config loaded successfully (" + configFile + ").");
        }

        // Check Java version
        double jvmVersion = 1.5;
        try {
            jvmVersion = Float.valueOf(
                    System.getProperty("java.version", "1.5").substring(0, 3)).floatValue();
        } catch (Exception ignored) {
        }
        if (jvmVersion < 1.5) {
            log.fatal("GCALDaemon requires at least Java 1.5! Current version: "
                    + System.getProperty("java.version"));
            throw new Exception("Invalid JVM version!");
        }

        // Check permission
        if (workDirectory.isDirectory() && !workDirectory.canWrite()) {
            if (System.getProperty("os.name", "unknown").toLowerCase().indexOf(
                    "windows") == -1) {
                String path = workDirectory.getCanonicalPath();
                if (programRootDir != null) {
                    path = programRootDir.getCanonicalPath();
                }
                log.warn("Please check the file permissions on the '"
                        + workDirectory.getCanonicalPath() + "' folder!\r\n"
                        + "Hint: [sudo] chmod -R 777 " + path);
            }
        }

        // Disable SSL validation
        try {
            // Create a trust manager that does not validate certificate chains
            javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                new javax.net.ssl.X509TrustManager() {

                    public final java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    public final void checkClientTrusted(
                            java.security.cert.X509Certificate[] certs,
                            String authType) {
                    }

                    public final void checkServerTrusted(
                            java.security.cert.X509Certificate[] certs,
                            String authType) {
                    }
                }
            };

            // Install the all-trusting trust manager
            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Throwable ignored) {
        }

        // Replace hostname verifier
        try {
            javax.net.ssl.HostnameVerifier hv[] = new javax.net.ssl.HostnameVerifier[]{
                new javax.net.ssl.HostnameVerifier() {

                    public final boolean verify(String hostName,
                            javax.net.ssl.SSLSession session) {
                        return true;
                    }
                }
            };

            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier(hv[0]);
        } catch (Throwable ignored) {
        }

        // Setup proxy
        String proxyHost = getConfigProperty(PROXY_HOST, null);
        if (proxyHost != null) {
            String proxyPort = getConfigProperty(PROXY_PORT, null);
            if (proxyPort == null) {
                log.warn("Missing 'proxy.port' configuration property!");
            } else {

                // HTTP proxy server properties
                System.setProperty("http.proxyHost", proxyHost);
                System.setProperty("http.proxyPort", proxyPort);
                System.setProperty("http.proxySet", "true");

                // HTTPS proxy server properties
                System.setProperty("https.proxyHost", proxyHost);
                System.setProperty("https.proxyPort", proxyPort);
                System.setProperty("https.proxySet", "true");

                // Setup proxy credentials
                String username = getConfigProperty(PROXY_USERNAME, null);
                String encodedPassword = getConfigProperty(PROXY_PASSWORD, null);
                if (username != null) {
                    if (encodedPassword == null) {
                        log.warn("Missing 'proxy.password' configuration property!");
                    } else {
                        String password = StringUtils.decodePassword(encodedPassword);

                        // HTTP auth credentials
                        System.setProperty("http.proxyUser", username);
                        System.setProperty("http.proxyUserName", username);
                        System.setProperty("http.proxyPassword", password);

                        // HTTPS auth credentials
                        System.setProperty("https.proxyUser", username);
                        System.setProperty("https.proxyUserName", username);
                        System.setProperty("https.proxyPassword", password);
                    }
                }
            }
        }

        // Get feed event duplication ratio
        String percent = getConfigProperty(FEED_DUPLICATION_FILTER, "70").trim();
        if (percent.endsWith("%")) {
            percent = percent.substring(0, percent.length() - 1).trim();
        }
        double ratio = Double.parseDouble(percent) / 100;
        if (ratio < 0.4) {
            ratio = 0.4;
            log.warn("The smallest enabled filter percent is '40%'!");
        } else {
            if (ratio > 1) {
                log.warn("The largest filter percent is '100%'!");
                ratio = 1;
            }
        }
        duplicationRatio = ratio;

        // Displays time zone
        log.info("Local time zone is " + TimeZone.getDefault().getDisplayName()
                + ".");

        // Get main thread group
        ThreadGroup mainGroup = Thread.currentThread().getThreadGroup();
        while (mainGroup.getParent() != null) {
            mainGroup = mainGroup.getParent();
        }

        // Init Gmail pool
        boolean enableLDAP = getConfigProperty(LDAP_ENABLED, false);
        if (enableLDAP) {
            gmailPool = startService(log, mainGroup, "org.gldapdaemon.core.GmailPool");
        }

        // Init LDAP listener
        if (enableLDAP) {
            contactLoader = startService(log, mainGroup, "org.gldapdaemon.core.ldap.ContactLoader");
        } else {
            if (standaloneMode) {
                log.info("LDAP server disabled.");
            }
        }

        // Clear configuration holder
        config.clear();
    }

    private final Thread startService(Log log, ThreadGroup group, String name) throws Exception {
        try {
            Class serviceClass = Class.forName(name);
            Class[] types = new Class[2];
            types[0] = ThreadGroup.class;
            types[1] = Configurator.class;
            Constructor constructor = serviceClass.getConstructor(types);
            Object[] values = new Object[2];
            values[0] = group;
            values[1] = this;
            return (Thread) constructor.newInstance(values);
        } catch (Exception configError) {
            String message = configError.getMessage();
            Throwable cause = configError.getCause();
            while (cause != null) {
                if (cause.getMessage() != null) {
                    message = cause.getMessage();
                }
                cause = cause.getCause();
            }
            log.fatal(message.toUpperCase(), configError);
            throw configError;
        }
    }

    public final byte getRunMode() {
        return mode;
    }

    public final File getConfigFile() {
        return configFile;
    }

    public static final void copyFile(File from, File to) throws Exception {
        if (from == null || to == null || !from.exists()) {
            return;
        }
        RandomAccessFile fromFile = null;
        RandomAccessFile toFile = null;
        try {
            fromFile = new RandomAccessFile(from, "r");
            toFile = new RandomAccessFile(to, "rw");
            FileChannel fromChannel = fromFile.getChannel();
            FileChannel toChannel = toFile.getChannel();
            long length = fromFile.length();
            long start = 0;
            while (start < length) {
                start += fromChannel.transferTo(start, length - start,
                        toChannel);
            }
            fromChannel.close();
            toChannel.close();
        } finally {
            if (fromFile != null) {
                fromFile.close();
            }
            if (toFile != null) {
                toFile.close();
            }
        }
    }

    // --- COMMON CONFIGURATION PROPERTY GETTERS ---
    public final String getConfigProperty(String name, String defaultValue) {
        String value = config.getProperty(name, defaultValue);
        if (value == null) {
            return defaultValue;
        } else {
            value = value.trim();
            if (value.length() == 0) {
                return defaultValue;
            }
        }
        return value;
    }

    public final boolean getConfigProperty(String name, boolean defaultValue) {
        String bool = config.getProperty(name, Boolean.toString(defaultValue)).toLowerCase();
        return "true".equals(bool) || "on".equals(bool) || "1".equals(bool);
    }

    public final long getConfigProperty(String name, long defaultValue) throws Exception {
        String number = config.getProperty(name, Long.toString(defaultValue));
        try {
            return StringUtils.stringToLong(number);
        } catch (Exception malformed) {
            throw new IllegalArgumentException("Malformed numeric parameter (" + name + ")!");
        }
    }

    public final FilterMask[] getFilterProperty(String name) throws Exception {
        return getFilterProperty(name, false);
    }

    public final FilterMask[] getFilterProperty(String name, boolean ignoreCase) throws Exception {
        String list = config.getProperty(name, null);
        try {
            return StringUtils.splitMaskList(list, ignoreCase);
        } catch (Exception malformed) {
            throw new IllegalArgumentException("Malformed mask list (" + name + ")!");
        }
    }

    public final String getPasswordProperty(String name) throws Exception {
        String encodedPassword = config.getProperty(name, null);
        if (encodedPassword == null) {
            throw new IllegalArgumentException("Missing password (" + name + ")!");
        }
        try {
            return StringUtils.decodePassword(encodedPassword);
        } catch (Exception malformed) {
            throw new IllegalArgumentException("Malformed password (" + name + ")!");
        }
    }

    public final File getWorkDirectory() {
        return workDirectory;
    }

    // --- GMAIL ADDRESS BOOK ---
    private volatile boolean started = false;

    public final ArrayList<GmailContact> getAddressBook() throws Exception {
        ArrayList<GmailContact> contacts = null;
        if (contactLoader != null) {
            ContactLoader loader = (ContactLoader) contactLoader;
            try {
                contacts = loader.getContacts();
                if (!started) {
                    started = true;
                    if (contacts == null) {
                        for (int i = 0; i < 5; i++) {
                            contacts = loader.getContacts();
                            if (contacts != null) {
                                break;
                            }
                            Thread.sleep(2000);
                        }
                    }
                }
            } catch (InterruptedException interrupt) {
                throw interrupt;
            } catch (Exception ignored) {
            }
        }
        return contacts;
    }

    // --- COMMON GMAIL POOL ---
    public final GmailPool getGmailPool() {
        return (GmailPool) gmailPool;
    }

    // --- STANDALONE APPLICATION MARKER ---
    public final boolean isStandalone() {
        return standaloneMode;
    }

    // --- STOP LISTENERS ---
    public final void interrupt() {

        // Stop services
        stopService(contactLoader);
        stopService(gmailPool);
    }

    private static final void stopService(Thread service) {
        if (service != null) {
            try {
                service.interrupt();
            } catch (Exception ignored) {
            }
        }
    }
}
