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

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.Window;
import java.net.URL;

/**
 * To enable progress monitor window set the 'progress.enabled' property in the
 * 'gcal-daemon.cof' to 'true'.
 * 
 * <pre>
 * 
 *  
 *    # Show progress monitor
 *    progress.enabled=true
 *   
 *  
 * </pre>
 * 
 * Created: Jan 03, 2007 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class ProgressMonitor extends Window {

	// --- SERIALVERSION ---

	private static final long serialVersionUID = -9003076562946387004L;

	// --- VARIABLES ---

	private Image image;

	// --- CONSTRUCTOR ---

	public ProgressMonitor() throws Exception {
		super(new Frame());
		int w = 214;
		int h = 15;
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		URL url = ProgressMonitor.class.getResource("progress.gif");
		image = toolkit.createImage(url);
		Dimension size = toolkit.getScreenSize();
		setBounds((size.width - w) / 2, (size.height - h) / 2, w, h);
		setAlwaysOnTop(true);
		validate();
	}

	// --- PAINT ---

	public final void update(Graphics g) {
		paint(g);
	}

	public final void paint(Graphics g) {
		if (image != null && g != null) {
			g.drawImage(image, 0, 0, this);
		}
	}

}
