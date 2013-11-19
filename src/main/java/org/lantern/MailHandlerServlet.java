package org.lantern;

import java.io.IOException;
import java.util.Enumeration;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.Header;
import javax.mail.internet.InternetAddress;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.Session;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import com.google.appengine.api.utils.SystemProperty;

import org.lantern.data.Dao;


@SuppressWarnings("serial")
public class MailHandlerServlet extends HttpServlet {

    private final transient Logger log = Logger.getLogger(getClass().getName());

    private static final Pattern ATTACHED_EMAIL_PATTERN
        = Pattern.compile("<(.+@.+\\..+)>");

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
    private static final String[] IGNORE = {".*@getlantern.org"};

    @Override
    public void doPost(final HttpServletRequest req,
                       final HttpServletResponse res)
            throws IOException {
        String sender;
        try {
            MimeMessage msg = new MimeMessage(
                                Session.getDefaultInstance(new Properties(), null),
                                req.getInputStream());
            String path = req.getPathInfo();
            if (DIRECT_PATHINFO.equals(path)) {
                log.info("Just using sender's email.");
                sender = extractSenderFromDirectEmail(msg);
            } else if (FORWARD_PATHINFO.equals(path)) {
                log.info("Searching for email in forwarded content.");
                sender = extractSenderFromForwardedEmail(msg);
            } else {
                log.severe("Received email at unknown address: " + path);
                return;
            }
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
        if (StringUtils.isEmpty(sender)) {
            log.severe("Ignoring message with no sender: " + sender);
            return;
        }
        if (shouldIgnore(sender)) {
            log.info("Ignoring " + sender);
            return;
        } else {
            log.info("Adding invite to " + sender);
            new Dao().addInvite(INVITER, sender, null);
        }
    }

    private String extractSenderFromForwardedEmail(MimeMessage msg)
            throws MessagingException, IOException {
        MimeMultipart mmp = (MimeMultipart)msg.getContent();
        MimeBodyPart bp = (MimeBodyPart)mmp.getBodyPart(0);
        String content = (String)bp.getContent();
        Matcher m = ATTACHED_EMAIL_PATTERN.matcher(content);
        if (!m.find()) {
            throw new RuntimeException("Couldn't find an email in " + content);
        }
        return m.group(1);
    }

    private String extractSenderFromDirectEmail(MimeMessage msg)
            throws MessagingException {
        Address[] from = msg.getFrom();
        if (from.length != 1) {
            throw new RuntimeException(
                    "I don't know what to do with messages with "
                    + from.length + " senders.");
        }
        return ((InternetAddress) from[0]).getAddress();
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
