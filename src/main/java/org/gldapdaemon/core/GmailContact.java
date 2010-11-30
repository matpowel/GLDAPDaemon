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

import com.google.gdata.data.Content;
import com.google.gdata.data.TextContent;
import com.google.gdata.data.contacts.ContactEntry;
import com.google.gdata.data.extensions.PhoneNumber;
import java.io.Serializable;

/**
 * Gmail contact container.
 * 
 * Created: Jan 03, 2008 12:50:56 PM
 * 
 * @author Andras Berkes
 */
public final class GmailContact implements Serializable {

    // --- BASIC CONTACT VARIABLES ---
    public String name = "";
    public String email = "";
    public String notes = "";
    // --- EXTENDED CONTACT VARIABLES ---
    public String description = "";
    public String mail = "";
    public String im = "";
    public String phone = "";
    public String mobile = "";
    public String pager = "";
    public String fax = "";
    public String company = "";
    public String title = "";
    public String other = "";
    public String address = "";

    public GmailContact(ContactEntry entry) {
        if (entry.getName() != null && entry.getName().getFullName() != null) {
            name = entry.getName().getFullName().getValue();
        }
        if (entry.getEmailAddresses().size() > 0) {
            email = entry.getEmailAddresses().get(0).getAddress();
        }
        if (entry.getJots().size() > 0) {
            notes = entry.getJots().get(0).getValue();
        }
        //mail = entry.get
        if (entry.getImAddresses().size() > 0) {
            im = entry.getImAddresses().get(0).getAddress();
        }
        boolean set = false;
        String defaultPhone = null;
        for (PhoneNumber number : entry.getPhoneNumbers()) {
            if (number.getRel() != null) {
                if (number.getRel().endsWith("mobile")) {
                    mobile = number.getPhoneNumber();
                    set = true;
                } else if (number.getRel().endsWith("home")) {
                    phone = number.getPhoneNumber();
                    set = true;
                } else if (number.getRel().endsWith("pager")) {
                    pager = number.getPhoneNumber();
                    set = true;
                } else if (number.getRel().endsWith("fax")) {
                    fax = number.getPhoneNumber();
                    set = true;
                }
            }
            defaultPhone = number.getPhoneNumber();
        }
        if (defaultPhone != null && !set) {
            phone = defaultPhone;
        }

        if (entry.getOrganizations().size() > 0) {
            if (entry.getOrganizations().get(0).getOrgName() != null) {
                company = entry.getOrganizations().get(0).getOrgName().getValue();
            }
        }
        title = entry.getTitle().getPlainText();
        if (entry.getPostalAddresses().size() > 0) {
            address = entry.getPostalAddresses().get(0).getValue();
        }
    }
}
