package org.lantern;

import java.io.IOException;
import java.net.URL;
import java.util.List;

import com.google.gdata.client.Query;
import com.google.gdata.client.contacts.ContactsService;
import com.google.gdata.data.contacts.CalendarLink;
import com.google.gdata.data.contacts.ContactEntry;
import com.google.gdata.data.contacts.ContactFeed;
import com.google.gdata.util.ServiceException;

public class ContactsUtil {

    //private static final Logger LOG = 
    //    Logger.getLogger(ContactsUtil.class.getName());
    
    
    public static boolean appearsToBeReal(final String username, 
        final String pwd) throws IOException, ServiceException {
        final ContactsService service = new ContactsService("Lantern");
        
        service.setUserCredentials(username, pwd);
        final String path;
        if (username.trim().endsWith("gmail.com")) {
            path = username + "/full";
        }
        else {
            path = username+"@gmail.com/full";
        }
        final URL feedUrl = new URL("http://www.google.com/m8/feeds/contacts/"
                + path);
        
        final Query query = new Query(feedUrl);
        query.setMaxResults(3000);
        
        
        //final ContactFeed feed = service.getFeed(feedUrl, ContactFeed.class);
        final ContactFeed feed =  service.query(query, ContactFeed.class);
        
        // Print the results
        //LOG.info(feed.getTitle().getPlainText());
        for (final ContactEntry entry : feed.getEntries()) {
            //printContact(entry);
            // printContact(entry);
            // Since 2.0, the photo link is always there, the presence of an
            // actual
            // photo is indicated by the presence of an ETag.
            /*
             * Link photoLink = entry.getLink(
             * "http://schemas.google.com/contacts/2008/rel#photo", "image/*");
             * if (photoLink.getEtag() != null) { Service.GDataRequest request =
             * service.createLinkQueryRequest(photoLink); request.execute();
             * InputStream in = request.getResponseStream();
             * ByteArrayOutputStream out = new ByteArrayOutputStream();
             * RandomAccessFile file = new RandomAccessFile( "/tmp/" +
             * entry.getSelfLink().getHref().substring(
             * entry.getSelfLink().getHref().lastIndexOf('/') + 1), "rw");
             * byte[] buffer = new byte[4096]; for (int read = 0; (read =
             * in.read(buffer)) != -1; out.write(buffer, 0, read)) {}
             * file.write(out.toByteArray()); file.close(); in.close();
             * request.end(); }
             */
        }
        final int total = feed.getEntries().size();
        //LOG.info("Total: " + total + " entries found");
        return total > 600;
    }
    
    public static int getNumContacts(final String username, 
        final String pwd) throws IOException, ServiceException {
        final ContactsService service = new ContactsService("Lantern");
        
        service.setUserCredentials(username, pwd);
        final String path;
        if (username.trim().endsWith("gmail.com")) {
            path = username + "/full";
        }
        else {
            path = username+"@gmail.com/full";
        }
        final URL feedUrl = new URL("http://www.google.com/m8/feeds/contacts/"
                + path);
        
        final Query query = new Query(feedUrl);
        query.setMaxResults(3000);
        
        
        //final ContactFeed feed = service.getFeed(feedUrl, ContactFeed.class);
        final ContactFeed feed =  service.query(query, ContactFeed.class);
        
        // Print the results
        //LOG.info(feed.getTitle().getPlainText());
        for (final ContactEntry entry : feed.getEntries()) {
            //printContact(entry);
            // printContact(entry);
            // Since 2.0, the photo link is always there, the presence of an
            // actual
            // photo is indicated by the presence of an ETag.
            /*
             * Link photoLink = entry.getLink(
             * "http://schemas.google.com/contacts/2008/rel#photo", "image/*");
             * if (photoLink.getEtag() != null) { Service.GDataRequest request =
             * service.createLinkQueryRequest(photoLink); request.execute();
             * InputStream in = request.getResponseStream();
             * ByteArrayOutputStream out = new ByteArrayOutputStream();
             * RandomAccessFile file = new RandomAccessFile( "/tmp/" +
             * entry.getSelfLink().getHref().substring(
             * entry.getSelfLink().getHref().lastIndexOf('/') + 1), "rw");
             * byte[] buffer = new byte[4096]; for (int read = 0; (read =
             * in.read(buffer)) != -1; out.write(buffer, 0, read)) {}
             * file.write(out.toByteArray()); file.close(); in.close();
             * request.end(); }
             */
        }
        final int total = feed.getEntries().size();
        //LOG.info("Total: " + total + " entries found");
        return total;
    }
    
    /**
     * Print the contents of a ContactEntry to System.err.
     *
     * @param contact The ContactEntry to display.
     */
    private static void printContact(final ContactEntry contact) {
        System.out.println("-------------------------------------------");
        final List<CalendarLink> links = contact.getCalendarLinks();
        for (final CalendarLink link : links) {
            System.out.println(link.getHref());
        }
        /*
        LOG.info("Id: " + contact.getId());
        if (contact.getTitle() != null) {
            LOG.info("Contact name: "
                    + contact.getTitle().getPlainText());
        } else {
            LOG.info("Contact has no name");
        }
        LOG.info("Last updated: " + contact.getUpdated().toUiString());
        if (contact.hasDeleted()) {
            LOG.info("Deleted:");
        }
        */

        ElementHelper.printContact(System.err, contact);

        
        /*
        final com.google.gdata.data.Link photoLink = contact.getLink(
                "http://schemas.google.com/contacts/2008/rel#photo", "image/*");
        LOG.info("Photo link: " + photoLink.getHref());
        String photoEtag = photoLink.getEtag();
        LOG.info("  Photo ETag: "
                + (photoEtag != null ? photoEtag
                        : "(No contact photo uploaded)"));
        LOG.info("Self link: " + contact.getSelfLink().getHref());
        LOG.info("Edit link: " + contact.getEditLink().getHref());
        LOG.info("ETag: " + contact.getEtag());
        */
        System.out.println("-------------------------------------------");
    }

    /*
    public static void main(final String[] args) {
        final Properties props = new Properties();
        final File lantern = 
            new File(System.getProperty("user.home"), ".lantern");
        final File file = 
            new File(lantern, "lantern.properties");
        try {
            props.load(new FileInputStream(file));
            final String user = props.getProperty("google.user");
            final String pwd = props.getProperty("google.pwd");
            if (StringUtils.isBlank(user)) {
                LOG.severe("No user name");
                throw new IllegalStateException("No user name in: " + file);
            }
            
            if (StringUtils.isBlank(pwd)) {
                LOG.severe("No password.");
                throw new IllegalStateException("No password in: " + file);
            }
            appearsToBeReal(user, pwd);
        } catch (final IOException e) {
            final String msg = "Error loading props file at: " + file;
            LOG.severe(msg + e);
            throw new RuntimeException(msg, e);
        } catch (final ServiceException e) {
            LOG.severe("Error looking up contacts" + e);
        }
    }
    */
}
