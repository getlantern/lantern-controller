package org.lantern;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Properties;
import java.util.Set;

import javax.mail.Address;
import javax.mail.internet.InternetAddress;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.Session;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.utils.SystemProperty;

import org.lantern.data.Dao;


@SuppressWarnings("serial")
public class MailHandlerServlet extends HttpServlet {

    private final transient Logger log = Logger.getLogger(getClass().getName());

    private static final Pattern ATTACHED_EMAIL_PATTERN;

    static {
        String validOuter = "a-zA-Z0-9_";
        String validInner = validOuter + ".+-";
        String outerClass = "[" + validOuter + "]";
        String innerClass = "[" + validInner + "]";
        ATTACHED_EMAIL_PATTERN = Pattern.compile(
                "oi*@i+\\.o+".replace("o", outerClass).replace("i", innerClass));
    }

    private static String pathInfoFromUsername(String username) {
        return "/" + username + "@" + SystemProperty.applicationId.get()
               + ".appspotmail.com";
    }

    /**
     * The email at which we're listening for direct invite requests.
     */
    private static final String DIRECT_PATHINFO
        = pathInfoFromUsername("invite");

    /**
     * The email at which we're listening for forwarded invite requests.
     *
     * We use this to process invite requests that predate the deployment of this
     * servlet.
     */
    private static final String FORWARD_PATHINFO
        = pathInfoFromUsername("forward");

    /**
     * The inviter for all invites triggered by this servlet.
     */
    private static final String INVITER = "invite@getlantern.org";

    /**
     * Ignore emails matching these patterns.
     *
     * For example, we get a message from noreply@getlantern.org when we're added
     * to the invite mailing list.
     */
    private static final String[] IGNORE = {".*@getlantern.org",
                                            ".*@googlegroups.com",
                                            ".*@.*\\.appspotmail.com"};

    @Override
    public void doPost(final HttpServletRequest req,
                       final HttpServletResponse res)
            throws IOException {
        Set<String> senders = new HashSet<String>();
        try {
            MimeMessage msg = new MimeMessage(
                                Session.getDefaultInstance(new Properties(), null),
                                req.getInputStream());
            String path = req.getPathInfo();
            if (DIRECT_PATHINFO.equals(path)) {
                log.info("Just using sender's email.");
                extractSendersFromDirectEmail(msg, senders);
            } else if (FORWARD_PATHINFO.equals(path)) {
                log.info("Searching for email in forwarded content.");
                extractSendersFromForwardedEmail(msg, senders);
            } else {
                log.severe("Received email at unknown address: " + path);
                return;
            }
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
        log.info("Got " + senders.size() + " senders.");
        Dao dao = new Dao();
        for (String sender : senders) {
            if (shouldIgnore(sender)) {
                log.info("Ignoring " + sender);
            } else {
                log.info("Adding invite to " + sender);
                dao.addInvite(INVITER, sender, null);
            }
        }
    }

    private void extractSendersFromForwardedEmail(MimeMessage msg, Set<String> accum)
            throws MessagingException, IOException {
        extractSendersFromMultipart((MimeMultipart)msg.getContent(), accum);
    }

    private void extractSendersFromMultipart(MimeMultipart mp, Set<String> accum)
            throws MessagingException, IOException {
        for (int i=0; i < mp.getCount(); i++) {
            MimeBodyPart bp = (MimeBodyPart)mp.getBodyPart(i);
            String type = bp.getContentType();
            if (type.startsWith("multipart/alternative")) {
                extractSendersFromMultipart((MimeMultipart)bp.getContent(), accum);
            } else if (type.startsWith("text/")) {
                extractSendersFromText((String)bp.getContent(), accum);
            } else if (type.startsWith("application/pgp-signature")) {
                // Ignore.
            } else {
                log.warning("Unknown type: " + type);
            }
        }
    }

    /**
     * Made public and static to simplify testing (and it's not using instance
     * state after all.)
     */
    public static void extractSendersFromText(String text, Set<String> accum) {
        Matcher m = ATTACHED_EMAIL_PATTERN.matcher(text);
        while (m.find()) {
            accum.add(m.group());
        }
    }

    private void extractSendersFromDirectEmail(MimeMessage msg, Set<String> accum)
            throws MessagingException {
        Address[] from = msg.getFrom();
        if (from.length != 1) {
            log.warning("Got " + from.length + " direct senders.");
        }
        for (int i=0; i < from.length ; i++) {
            accum.add(((InternetAddress) from[i]).getAddress());
        }
    }

    private boolean shouldIgnore(String sender) {
        for (String ignorePattern : IGNORE) {
            if (sender.matches(ignorePattern)) {
                return true;
            }
        }
        return false;
    }
}
