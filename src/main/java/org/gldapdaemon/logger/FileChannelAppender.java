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
package org.gldapdaemon.logger;

import java.io.File;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.charset.CharsetEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LoggingEvent;
import org.gldapdaemon.core.StringUtils;

/**
 * Log4J compatible, NIO-based file appender.
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class FileChannelAppender extends AppenderSkeleton {

	private static final int BUFFER_SIZE = 3000;
	private static final char[] LT = "  [".toCharArray();
	private static final char[] GT = "] ".toCharArray();
	private static final char[] CRLF = "\r\n".toCharArray();

	private String directory;
	private String logFileName = "gcal-daemon";
	private String archivedMask = "yyyy-MM-dd-z-HH-mm-ss";
	private String encoding = "ISO-8859-1";
	private long timeout = 604800000L;
	private long maxSize = 104857600L;
	private long maxFiles = 30;

	private FileChannel channel;
	private CharsetEncoder encoder;
	private int limit;

	private final ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
	private final QuickWriter writer = new QuickWriter(BUFFER_SIZE);

	public final void activateOptions() {
		try {
			long removeBefore = System.currentTimeMillis() - timeout;

			// Init log dir
			File logDirectory = null;
			if (directory != null) {
				if ("autodetect".equalsIgnoreCase(directory)) {
					directory = System.getProperty("gldapdaemon.program.dir", "") + "/log";
				}
				logDirectory = new File(directory);
			}
			if (logDirectory == null || !logDirectory.isDirectory()) {
				String path = (new File("x")).getAbsolutePath().replace('\\',
						'/');
				int i = path.lastIndexOf('/');
				if (i > 1) {
					i = path.lastIndexOf('/', i - 1);
					if (i > 1) {
						logDirectory = new File(path.substring(0, i), "log");
					}
				}
				if (logDirectory == null || !logDirectory.isDirectory()) {
					logDirectory = new File("/usr/local/sbin/GCALDaemon/log");
				}
				if (logDirectory == null || !logDirectory.isDirectory()) {
					File root = new File("/");
					String[] dirs = root.list();
					if (dirs != null) {
						for (i = 0; i < dirs.length; i++) {
							logDirectory = new File('/' + dirs[i]
									+ "/GCALDaemon/log");
							if (logDirectory.isDirectory()) {
								break;
							}
						}
					}
				}
			}
			if (logDirectory == null || !logDirectory.isDirectory()) {
				logDirectory = new File("log");
				logDirectory.mkdirs();
			}

			// Cleanup log dir
			File[] files = logDirectory.listFiles();
			for (int i = 0; i < files.length; i++) {
				File logFile = files[i];
				if (logFile.getName().endsWith(".log")
						&& (logFile.lastModified() < removeBefore)) {
					logFile.delete();
				}
			}
			files = logDirectory.listFiles();
			if (files.length > maxFiles && maxFiles > 0) {
				for (int n = 0; n < (files.length - maxFiles); n++) {
					File logFile = files[n];
					if (logFile.getName().endsWith(".log")) {
						logFile.delete();
					}
				}
			}

			// Archive old log file
			File file = new File(logDirectory, logFileName + ".log");
			if (file.exists()) {
				SimpleDateFormat nameFormat = new SimpleDateFormat(archivedMask);
				Date date = new Date(file.lastModified());
				String fileName = nameFormat.format(date);
				file.renameTo(new File(logDirectory, fileName + ".log"));
			}

			// Init new log file
			RandomAccessFile raf = new RandomAccessFile(file, "rw");
			raf.setLength(0);
			channel = raf.getChannel();
			encoder = StringUtils.getEncoder(encoding);
			limit = (int) (BUFFER_SIZE / Math.ceil(encoder
					.averageBytesPerChar()));
		} catch (Exception e) {
			LogLog.error("Unable to init logger!", e);
			if (System.getProperty("os.name", "unknown").toLowerCase().indexOf(
					"windows") == -1) {
				LogLog.warn("Please check the file permissions on the "
						+ "'log' folder!\r\n"
						+ "Hint: [sudo] chmod -R 777 "
						+ System.getProperty("gldapdaemon.program.dir", "/usr/local/sbin/GCALDaemon"));
			}
			channel = null;
			encoder = null;
		}
	}

	protected final void append(LoggingEvent event) {
		if (channel != null && encoder != null) {

			// Check size
			try {
				if (channel.position() >= maxSize) {
					channel.close();
					activateOptions();
				}
			} catch (ClosedByInterruptException interrupt) {
				channel = null;
				return;
			} catch (Exception e) {
				LogLog.error("Unable to reopen logger!", e);
				reopen();
			}

			// Create log
			writer.write(layout.format(event));
			if (layout.ignoresThrowable()) {
				try {
					String[] s = event.getThrowableStrRep();
					if (s != null) {
						writer.write(CRLF);
						int len = s.length;
						for (int i = 0; i < len; i++) {
							writer.write(LT);
							writer.write(Integer.toString(i + 1));
							if (i < 9) {
								writer.write(' ');
							}
							writer.write(GT);
							writer.write(s[i].trim());
							writer.write(CRLF);
						}
						writer.write(CRLF);
					}
				} catch (Exception e1) {
					try {
						event.getThrowableInformation().getThrowable()
								.printStackTrace(new PrintWriter(writer, true));
					} catch (Exception e2) {
						// Ignore
					}
				}
			}

			// Flush content
			char[] array = writer.getChars();
			writer.flush();
			int max = array.length;
			if (max > limit) {
				int length;
				for (int offset = 0; offset < max; offset += limit) {
					length = Math.min(limit, max - offset);
					write(array, offset, length);
				}
			} else {
				write(array, 0, max);
			}
		}
	}

	private boolean reopened;

	private final void write(char[] array, int offset, int length) {
		try {
			encoder.reset();
			buffer.clear();
			CharBuffer chars = CharBuffer.wrap(array, offset, length);
			encoder.encode(chars, buffer, false);
			buffer.flip();
			while (buffer.hasRemaining()) {
				channel.write(buffer);
			}
			reopened = false;
		} catch (ClosedByInterruptException i) {
			if (!reopened) {
				reopen();
				reopened = true;
				write(array, offset, length);
			}
		} catch (Exception e) {
			LogLog.error("Unable to write log file!", e);
		}
	}

	private final void reopen() {
		try {
			File logDirectory = new File(directory);
			File file = new File(logDirectory, logFileName + ".log");
			RandomAccessFile raf = new RandomAccessFile(file, "rw");
			if (file.exists()) {
				raf.seek(file.length());
			}
			channel = raf.getChannel();
		} catch (Exception e) {
			LogLog.error("Unable to reopen logger!", e);
			channel = null;
		}
	}

	public final void close() {
		if (writer.length() > 0) {
			char[] array = writer.getChars();
			writer.flush();
			int max = array.length;
			if (max > limit) {
				int length;
				for (int offset = 0; offset < max; offset += limit) {
					length = Math.min(limit, max - offset);
					write(array, offset, length);
				}
			} else {
				write(array, 0, max);
			}
		}
		if (channel != null) {
			try {
				channel.close();
				channel = null;
				encoder = null;
			} catch (Exception e) {
				LogLog.error("Unable to close log file!", e);
			}
		}
	}

	public final boolean requiresLayout() {
		return true;
	}

	// --- CONFIGURATION METHODS ---

	public final String getEncoding() {
		return encoding;
	}

	public final String getDirectory() {
		return directory;
	}

	public final void setEncoding(String string) {
		encoding = string;
	}

	public final void setDirectory(String string) {
		directory = string;
	}

	public final String getArchivedMask() {
		return archivedMask;
	}

	public final void setArchivedMask(String string) {
		archivedMask = string;
	}

	public final String getLogTimeout() {
		return String.valueOf(timeout);
	}

	public final void setLogTimeout(String time) {
		try {
			timeout = StringUtils.stringToLong(time);
		} catch (Exception e) {
			System.err.println("Invalid log timeout value: " + time);
		}
	}

	public final String getMaxSize() {
		return String.valueOf(maxSize);
	}

	public final void setMaxSize(String size) {
		try {
			maxSize = StringUtils.stringToLong(size);
		} catch (Exception e) {
			System.err.println("Invalid maximum size: " + size);
		}
	}

	public final String getLogFileName() {
		return logFileName;
	}

	public final void setLogFileName(String currentLogName) {
		this.logFileName = currentLogName;
	}

	public final String getMaxFiles() {
		return String.valueOf(maxFiles);
	}

	public final void setMaxFiles(String count) {
		try {
			maxFiles = StringUtils.stringToLong(count);
		} catch (Exception e) {
			System.err.println("Invalid number of log files: " + count);
		}
	}
}
