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

import java.io.Writer;

/**
 * Unsynchronized StringBuffer-like class for the faster string concatenation.
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class QuickWriter extends Writer {

	private char[] buffer;
	private int length;

	// --- CONSTRUCTORS ---

	public QuickWriter(int initialSize) {
		buffer = new char[initialSize];
	}

	public QuickWriter() {
		buffer = new char[1024];
	}

	public final void close() {
		flush();
	}

	public final void flush() {
		length = 0;
		if (buffer.length > 20000) {
			buffer = new char[2048];
		}
	}

	/**
	 * Expands the buffer.
	 * 
	 * @param newLength
	 */
	private final void expandBuffer(int newLength) {
		int doubleLength = newLength * 2;
		if (doubleLength < 0) {
			doubleLength = Integer.MAX_VALUE;
		}
		char copy[] = new char[doubleLength];
		System.arraycopy(buffer, 0, copy, 0, length);
		buffer = copy;
	}

	/**
	 * Returns the length of the buffer.
	 * 
	 * @return int
	 */
	public final int length() {
		return length;
	}

	/**
	 * Sets the length of the buffer.
	 * 
	 * @param newLength
	 *            new length
	 */
	public final void setLength(int newLength) {
		if (length != newLength) {
			if (length > newLength) {
				length = newLength;
			} else {
				write(' ', newLength - length);
			}
		}
	}

	/**
	 * Returns the buffer's characters.
	 * 
	 * @return char[]
	 */
	public final char[] getChars() {
		char[] copy = new char[length];
		System.arraycopy(buffer, 0, copy, 0, length);
		return copy;
	}

	/**
	 * Returns the buffer' content as ASCII bytes.
	 * 
	 * @return byte[]
	 */
	public final byte[] getBytes() {
		byte[] bytes = new byte[length];
		for (int i = 0; i < length; i++) {
			bytes[i] = (byte) buffer[i];
		}
		return bytes;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Object#toString()
	 */
	public final String toString() {
		return new String(buffer, 0, length);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.Writer#write(char[], int, int)
	 */
	public final void write(char[] chars, int off, int len) {
		int newLength = length + len;
		if (newLength > buffer.length) {
			expandBuffer(newLength);
		}
		System.arraycopy(chars, off, buffer, length, len);
		length = newLength;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.Writer#write(java.lang.String)
	 */
	public final void write(String str) {
		if (str != null) {
			int len = str.length();
			if (len != 0) {
				int newLength = length + len;
				if (newLength > buffer.length) {
					expandBuffer(newLength);
				}
				str.getChars(0, len, buffer, length);
				length = newLength;
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.Writer#write(char[])
	 */
	public final void write(char[] chars) {
		int newLength = length + chars.length;
		if (newLength > buffer.length) {
			expandBuffer(newLength);
		}
		System.arraycopy(chars, 0, buffer, length, chars.length);
		length = newLength;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.Writer#write(char[])
	 */
	public final void write(QuickWriter writer) {
		int newLength = length + writer.length;
		if (newLength > buffer.length) {
			expandBuffer(newLength);
		}
		System.arraycopy(writer.buffer, 0, buffer, length, writer.length);
		length = newLength;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.Writer#write(int)
	 */
	public final void write(int character) {
		int newLength = length + 1;
		if (newLength > buffer.length) {
			expandBuffer(newLength);
		}
		buffer[length++] = (char) character;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.io.Writer#write(java.lang.String, int, int)
	 */
	public final void write(String str, int off, int len) {
		int newLength = length + len;
		if (newLength > buffer.length) {
			expandBuffer(newLength);
		}
		str.getChars(off, off + len, buffer, length);
		length = newLength;
	}

	/**
	 * Writes characters.
	 * 
	 * @param c
	 * @param repeats
	 */
	public final void write(char c, int repeats) {
		int newLength = length + repeats;
		if (newLength > buffer.length) {
			expandBuffer(newLength);
		}
		for (int i = length; i < newLength; i++) {
			buffer[i] = c;
		}
		length = newLength;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.lang.Appendable#append(char)
	 */
	public final Writer append(char c) {
		int newLength = length + 1;
		if (newLength > buffer.length) {
			expandBuffer(newLength);
		}
		buffer[length++] = c;
		return this;
	}

}