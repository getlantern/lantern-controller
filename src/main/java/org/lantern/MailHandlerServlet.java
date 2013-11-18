package org.lantern;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
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

    /**
     * Soft sanity check.
     *
     * We're not enforcing this but logging warnings if we get email to the
     * wrong address.
     */
    private static final String EXPECTED_PATHINFO
        = "/invite@" + SystemProperty.applicationId.get() + ".appspotmail.com";

    /** The inviter for all invites triggered by this servlet. */
    private static final String INVITER = "invite@getlantern.org";

    @Override
    public void doPost(final HttpServletRequest req,
                       final HttpServletResponse res)
            throws IOException {
        if (!EXPECTED_PATHINFO.equals(req.getPathInfo())) {
            log.warning("Got message to unexpected endpoint: "
                        + req.getPathInfo());
        }
        Address[] from;
        try {
            from = new MimeMessage(
                        Session.getDefaultInstance(new Properties(), null),
                        req.getInputStream()).getFrom();
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
        if (from == null) {
            log.warning("Ignoring message with null sender.");
            return;
        }
        if (from.length != 1) {
            log.warning("Got " + from.length + " addresses?");
        }
        Dao dao = new Dao();
        for (Address address : from) {
            String senderEmail = ((InternetAddress) address).getAddress();
            if (StringUtils.isBlank(senderEmail)) {
                log.severe("Message from no sender? " + senderEmail);
            } else {
                log.info("Adding invite to " + senderEmail);
                dao.addInvite(INVITER, senderEmail, null);
            }
        }
    }
}
