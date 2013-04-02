package org.lantern;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.mrbean.MrBeanModule;
import org.lantern.data.Dao;
import org.lantern.state.Mode;
import org.w3c.dom.Document;

import com.google.appengine.api.xmpp.Message;
import com.google.appengine.api.xmpp.MessageBuilder;
import com.google.appengine.api.xmpp.MessageType;
import com.google.appengine.api.xmpp.Presence;
import com.google.appengine.api.xmpp.XMPPService;
import com.google.appengine.api.xmpp.XMPPServiceFactory;

@SuppressWarnings("serial")
public class XmppAvailableServlet extends HttpServlet {

    private final transient Logger log = Logger.getLogger(getClass().getName());

    @Override
    public void doPost(final HttpServletRequest req,
        final HttpServletResponse res) {
        log.info("Got XMPP post...");
        final XMPPService xmpp = XMPPServiceFactory.getXMPPService();
        final Presence presence;
        try {
            presence = xmpp.parsePresence(req);
        } catch (final IOException e) {
            log.severe("Could not parse presence: "+e.getMessage());
            return;
        }

        Document doc = LanternControllerUtils.buildDoc(presence);

        final Map<String,Object> responseJson =
                new LinkedHashMap<String,Object>();
        final Dao dao = new Dao();
        final String from = LanternControllerUtils.userId(presence);
        if (!dao.isInvited(from)) {
            log.info(from+" not invited!!");
            processNotInvited(presence, xmpp, responseJson);
            return;
        } else {
            log.info("User is invited: " + presence.getFromJid());
            dao.updateLastAccessed(from);
            responseJson.put(LanternConstants.INVITED, Boolean.TRUE);
        }

        final String userId = LanternXmppUtils.jidToUserId(from);
        final String instanceId = LanternControllerUtils.instanceId(presence);

        if (isInvite(doc)) {
            log.info("Got invite in stanza: "+presence.getStanza());
            if (!dao.hasMoreInvites(from)) {
                log.severe("No more invites for user: "+from);
                return;
            }
            processInvite(presence, doc);
            return;
        }

        if (!presence.isAvailable()) {
            log.info(userId + "/" + instanceId + " logging out.");
            dao.setInstanceUnavailable(userId, instanceId);
            return;
        }
        String modeStr = LanternControllerUtils.getProperty(doc, "mode");
        Mode mode;
        if ("give".equals(modeStr)) {
            mode = Mode.give;
        } else if ("get".equals(modeStr)) {
            mode = Mode.get;
        } else {
            // We don't believe the controller should ever get unknown mode
            // presences.
            log.warning("Ignoring presence in '" + modeStr + "' mode.");
            return;
        }

        final String stats =
            LanternControllerUtils.getProperty(doc, "stats");

        processClientInfo(presence, stats, responseJson, userId, instanceId, mode);

        if (mode == Mode.give) {
            processGiveMode(presence, xmpp, responseJson);
        } else {
            processGetMode(presence, xmpp, responseJson);
        }

        dao.signedIn(from);
    }

    private final class AlreadyInvitedException extends Exception {}

    private void processInvite(final Presence presence, final Document doc) {
        // XXX this is really a jabberid, email template makes it a "mailto:" link
        final String inviterEmail = LanternControllerUtils.userId(presence);
        final String inviterName;
        final String inviterNameTmp =
            LanternControllerUtils.getProperty(doc,
                LanternConstants.INVITER_NAME);
        if (StringUtils.isBlank(inviterNameTmp)) {
            inviterName = inviterEmail;
        } else {
            inviterName = inviterNameTmp;
        }
        final String invitedEmail =
            LanternControllerUtils.getProperty(doc,
                LanternConstants.INVITED_EMAIL);

        if (StringUtils.isBlank(invitedEmail)) {
            log.severe("No e-mail to invite?");
            return;
        }
        if (invitedEmail.contains("public.talk.google.com")) {
            // This is a google talk JID and not an e-mail address -- we
            // can't use it!.
            log.info("Can't e-mail a Google Talk ID. Ignoring.");
            return;
        }
        final Dao dao = new Dao();
        if (dao.getUserCount() >= LanternControllerConstants.MAX_USERS) {
            log.warning("We're out of slots for users, so we can't invite " + invitedEmail);
            sendTooManyUsersEmail();
            return;
        }
        final String refreshToken = LanternControllerUtils.getProperty(
                doc, LanternConstants.INVITER_REFRESH_TOKEN);
        if (refreshToken == null) {
            log.info("No refresh token.");
        } else {
            log.info("Refresh token starts with: "
                     + refreshToken.substring(0, 12) + "...");
        }
        InvitedServerLauncher.onInvite(
                inviterName, inviterEmail, refreshToken, invitedEmail);
    }

    private void sendTooManyUsersEmail() {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        String msgBody = "We have more than "
                + LanternControllerConstants.MAX_USERS
                + " users now.  WE had better up the limit or something.";
        MimeMessage msg = new MimeMessage(session);

        try {
            InternetAddress fromAddress = new InternetAddress(
                    LanternControllerConstants.ADMIN_EMAIL,
                    "Lantern Controller");
            msg.setFrom(fromAddress);
            InternetAddress toAddress = new InternetAddress(
                    LanternControllerConstants.NOTIFY_ON_MAX_USERS,
                    "Ops");
            msg.addRecipient(javax.mail.Message.RecipientType.TO,
                    toAddress);

            msg.setSubject("We're out of users!");
            msg.setText(msgBody);
            Transport.send(msg);

        } catch (AddressException e) {
            throw new RuntimeException(e);
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isInvite(final Document doc) {
        final String invite = LanternControllerUtils.getProperty(doc,
            LanternConstants.INVITED_EMAIL);
        boolean isInvite = !StringUtils.isBlank(invite);
        if (isInvite) {
            log.info("FOUND INVITE");
        } else {
            log.info("NO INVITE");
        }
        return isInvite;
    }

    private void processGetMode(final Presence presence,
        final XMPPService xmpp, final Map<String, Object> responseJson) {
        // We don't tell get mode users to check back in -- we just give them
        // servers to connect to.
        log.info("Sending servers to available get mode");
        addServers(presence.getFromJid().getId(), responseJson);
        sendResponse(presence, xmpp, responseJson);
    }

    private void processGiveMode(final Presence presence,
        final XMPPService xmpp, final Map<String, Object> responseJson) {
        // We always need to tell the client to check back in because we use
        // it as a fallback for which users are online.
        responseJson.put(LanternConstants.UPDATE_TIME,
            LanternControllerConstants.UPDATE_TIME_MILLIS);
        log.info("Not sending servers to give mode");
        sendResponse(presence, xmpp, responseJson);
    }


    private void processNotInvited(final Presence presence,
        final XMPPService xmpp, final Map<String, Object> responseJson) {
        responseJson.put(LanternConstants.INVITED, Boolean.FALSE);
        log.info("Not allowing uninvited user.");
        sendResponse(presence, xmpp, responseJson);
    }

    private void processClientInfo(final Presence presence,
        final String stats, final Map<String, Object> responseJson,
        final String idToUse, String instanceId, Mode mode) {

        if (StringUtils.isBlank(stats)) {
            log.info("No stats to process!");
            return;
        }
        log.info("Processing stats!");
        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new MrBeanModule());
        try {
            final Stats data = mapper.readValue(stats, Stats.class);
            addInviteData(presence, responseJson);
            // The following will delete the instance if it's not available,
            // updating all counters.
            log.info("Setting instance availability");
            final Dao dao = new Dao();
            dao.setInstanceAvailable(idToUse, instanceId, data.getCountryCode(), mode);
            try {
                updateStats(data, idToUse, instanceId, mode);
            } catch (final UnsupportedOperationException e) {
                log.severe("Error updating stats: "+e.getMessage());
            }
        } catch (final JsonParseException e) {
            log.severe("Error parsing stats: "+e.getMessage());
        } catch (final JsonMappingException e) {
            log.severe("Error parsing stats: "+e.getMessage());
        } catch (final IOException e) {
            log.severe("Error parsing stats: "+e.getMessage());
        }
    }

    private void addInviteData(final Presence presence,
        final Map<String, Object> responseJson) {

        final Dao dao = new Dao();
        final int invites =
            dao.getInvites(LanternControllerUtils.userId(presence));
        responseJson.put(LanternConstants.INVITES_KEY, invites);

    }

    private void sendResponse(final Presence presence, final XMPPService xmpp,
        final Map<String, Object> responseJson) {
        final String serversBody = JsonUtils.jsonify(responseJson);
        final Message msg =
            new MessageBuilder().withRecipientJids(
                presence.getFromJid()).withBody(serversBody).withMessageType(
                    MessageType.HEADLINE).build();
        log.info("Sending response:\n"+responseJson.toString());
        xmpp.sendMessage(msg);
    }

    private void updateStats(final Stats data, final String idToUse,
            final String instanceId, Mode mode) {

        final Dao dao = new Dao();

        log.info("Updating stats");
        dao.updateUser(idToUse, data.getDirectRequests(),
            data.getDirectBytes(), data.getTotalProxiedRequests(),
            data.getTotalBytesProxied(),
            data.getCountryCode(), instanceId, mode);
    }

    private void addServers(final String jid,
        final Map<String, Object> responseJson) {

        log.info("Adding server...");

        responseJson.put(LanternConstants.SERVERS,
                         Arrays.asList("75.101.134.244:7777"));
    }
}
