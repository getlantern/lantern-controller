package org.lantern;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.mrbean.MrBeanModule;
import org.json.simple.JSONObject;
import org.lantern.data.Dao;
import org.lantern.data.LegacyFriend;
import org.lantern.data.LegacyFriends;
import org.lantern.state.Mode;
import org.w3c.dom.Document;

import com.google.appengine.api.xmpp.JID;
import com.google.appengine.api.xmpp.Message;
import com.google.appengine.api.xmpp.MessageBuilder;
import com.google.appengine.api.xmpp.MessageType;
import com.google.appengine.api.xmpp.Presence;
import com.google.appengine.api.xmpp.XMPPService;
import com.google.appengine.api.xmpp.XMPPServiceFactory;

import com.googlecode.objectify.NotFoundException;


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

        final String userId = LanternXmppUtils.jidToEmail(from);
        final String resource = LanternControllerUtils.resourceId(presence);
        final String instanceId = LanternControllerUtils.getProperty(doc,
                "instanceId");
        final String hostAndPort = LanternControllerUtils.getProperty(doc,
                LanternConstants.HOST_AND_PORT);

        String fallbackConfigCookie = LanternControllerUtils.getProperty(
                doc, LanternConstants.FALLBACK_COOKIE);
        String fallbackHostAndPort;
        if (StringUtils.isBlank(fallbackConfigCookie)) {
            // Backwards compatibility.
            fallbackHostAndPort = LanternControllerUtils.getProperty(
                    doc, LanternConstants.FALLBACK_HOST_AND_PORT);
        } else {
            // The fallbackConfigCookie has a <scheme>|<payload> format, where
            // <scheme> must currently be "bc" (backwards compatibility) and
            // the rest is just the fallback host:port.
            String[] parts = fallbackConfigCookie.split("\\|");
            if (parts.length == 2 && parts[0] == "bc") {
                fallbackHostAndPort = parts[1];
            } else {
                log.severe("Unrecognized fallbackConfigCookie: '"
                           + fallbackConfigCookie + "'");
                fallbackHostAndPort = null;
            }
        }

        final boolean isFallbackProxy = "true".equalsIgnoreCase(
                LanternControllerUtils.getProperty(doc,
                        LanternConstants.IS_FALLBACK_PROXY));
        
        if (!presence.isAvailable()) {
            log.info(userId + "/" + resource + " logging out.");
            dao.setInstanceUnavailable(userId, resource);
            return;
        }
        
        if (isInvite(doc)) {
            log.info("Got invite in stanza: "+presence.getStanza());
            final String invitedEmail =
                    LanternControllerUtils.getProperty(doc,
                        LanternConstants.INVITED_EMAIL);
            queueInvite(xmpp, presence, doc, invitedEmail);
            return;
        }

        //handleFriendsSync(doc, presence.getFromJid(), xmpp);

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

        final String name =
                LanternControllerUtils.getProperty(doc, "name");

        processClientInfo(presence, stats, userId, instanceId,
                name, mode, resource, hostAndPort, fallbackHostAndPort,
                isFallbackProxy);

        handleVersionUpdate(doc, responseJson);
        sendUpdateTime(presence, xmpp, responseJson);

        final String language =
                LanternControllerUtils.getProperty(doc, "language");

        dao.signedIn(from, language);
    }

    private void handleVersionUpdate(Document doc, Map<String, Object> responseJson) {
        String s = LanternControllerUtils.getProperty(doc, LanternConstants.UPDATE_KEY);
        if (StringUtils.isBlank(s)) {
            log.info("no version info received, not sending any update info");
            return;
        }
        SemanticVersion clientVersion = SemanticVersion.from(s);
        log.info("clientVersion: " + clientVersion.toString());
        Dao dao = new Dao();
        LanternVersion latestVersion;
        try {
            latestVersion = dao.getLatestLanternVersion();
        } catch (NotFoundException e) {
            log.severe("No latest version set in this controller?");
            return;
        }
        log.info("latestVersion: " + latestVersion.toString());
        if (clientVersion.compareTo(latestVersion) < 0) {
            log.info("clientVersion < latestVersion, sending update notification");
            Map<String, Object> map = latestVersion.toMap();
            String installerUrl = "https://s3.amazonaws.com/lantern/latest";
            String os = LanternControllerUtils.getProperty(doc, LanternConstants.OS_KEY);
            log.info("os: " + os);
            if (StringUtils.isBlank(os)) {
                log.info("blank os, bailing");
                return;
            } else if (StringUtils.equalsIgnoreCase(os, "windows")) {
                installerUrl += ".exe";
            } else if (StringUtils.equalsIgnoreCase(os, "osx")) {
                installerUrl += ".dmg";
            } else if (StringUtils.equalsIgnoreCase(os, "ubuntu")) {
                String arch = LanternControllerUtils.getProperty(doc, LanternConstants.ARCH_KEY);
                log.info("arch: " + arch);
                if (arch.contains("64")) {
                    installerUrl += "-64.deb";
                } else {
                    installerUrl += "-32.deb";
                }
            } else {
                log.info("unexpected os: " + os + ", bailing");
                return;
            }
            log.info("sending installerUrl " + installerUrl);
            map.put("installerUrl", installerUrl);
            JSONObject o = new JSONObject(map);
            responseJson.put(LanternConstants.UPDATE_KEY, o);
            log.info("sending update info: " + o.toJSONString());
        }
    }

    private boolean handleFriendsSync(Document doc, JID fromJid, XMPPService xmpp) {
        //handle friends sync
        final String friendsJson =
                LanternControllerUtils.getProperty(doc, LanternConstants.FRIENDS);

        log.info("Handling friend sync");
        Dao dao = new Dao();

        String userId = LanternXmppUtils.jidToEmail(fromJid.getId());

        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new MrBeanModule());

        if (StringUtils.isEmpty(friendsJson)) {

            final String friendJson =
                    LanternControllerUtils.getProperty(doc, LanternConstants.FRIEND);
            if (StringUtils.isEmpty(friendJson)) {
                return false;
            }

            log.info("Syncing single friend");
            LegacyFriend clientFriend = safeMap(friendJson, mapper, LegacyFriend.class);
            dao.syncFriend(userId, clientFriend);
            return true;
        }


        LegacyFriends clientFriends = safeMap(friendsJson, mapper, LegacyFriends.class);

        log.info("Synced friends count = " + clientFriends.getFriends().size());

        List<LegacyFriend> changed = dao.syncFriends(userId, clientFriends);
        log.info("Changed friends count = " + changed.size());
        if (changed.size() > 0) {
            Map<String, Object> response = new HashMap<String, Object>();
            response.put(LanternConstants.FRIENDS, changed);
            String json = JsonUtils.jsonify(response);

            Message msg = new MessageBuilder()
                    .withRecipientJids(fromJid).withBody(json)
                    .withMessageType(MessageType.HEADLINE).build();
            log.info("Sending response:\n" + json.toString());
            xmpp.sendMessage(msg);
        }

        return true;
    }

    private <T> T safeMap(final String json, final ObjectMapper mapper, Class<T> cls) {
        try {
            return mapper.readValue(json, cls);
        } catch (final UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        } catch (final JsonParseException e) {
            log.severe("Error parsing client message: " + e.getMessage());
            throw new RuntimeException(e);
        } catch (final JsonMappingException e) {
            log.severe("Error parsing client message: " + e.getMessage());
            throw new RuntimeException(e);
        } catch (IOException e) {
            log.severe("Error reading client message: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void inviteSucceeded(XMPPService xmpp, Presence presence,
            String invitedEmail) {
        HashMap<String, Object> responseJson = new HashMap<String, Object>();
        List<String> invited = Arrays.asList(invitedEmail);
        responseJson.put(LanternConstants.INVITED_KEY, invited);
        sendResponse(presence, xmpp, responseJson);
    }

    private void inviteFailed(final XMPPService xmpp, final Presence presence,
            final String invitedEmail, String reason) {
        HashMap<String, Object> responseJson = new HashMap<String, Object>();
        final Map<String, Object> failedInvite = new HashMap<String, Object>();
        failedInvite.put(LanternConstants.INVITED_EMAIL, invitedEmail);
        failedInvite.put(LanternConstants.INVITE_FAILED_REASON, reason);
        final List<Map<String, Object>> failedInvites = new ArrayList<Map<String, Object>>();
        failedInvites.add(failedInvite);
        responseJson.put(LanternConstants.FAILED_INVITES_KEY, failedInvites);
        sendResponse(presence, xmpp, responseJson);
    }

    private final class AlreadyInvitedException extends Exception {}

    private void queueInvite(XMPPService xmpp, final Presence presence, final Document doc,
            final String invitedEmail) {
        // XXX this is really a jabberid, email template makes it a "mailto:" link
        final String inviterEmail = LanternControllerUtils.userId(presence);

        if (StringUtils.isBlank(invitedEmail)) {
            log.severe("No e-mail to invite?");
            inviteFailed(xmpp, presence, invitedEmail, "Blank invite");
            return;
        }
        if (invitedEmail.contains("public.talk.google.com")) {
            // This is a google talk JID and not an e-mail address -- we
            // can't use it!.
            log.info("Can't e-mail a Google Talk ID. Ignoring.");
            inviteFailed(xmpp, presence, invitedEmail, "Bad address");
            return;
        }
        final String refreshToken = LanternControllerUtils.getProperty(
                doc, LanternConstants.INVITER_REFRESH_TOKEN);
        if (refreshToken == null) {
            log.info("No refresh token.");
            //do not even queue invite, because no refresh token
            return;
        } else {
            log.info("Refresh token starts with: "
                     + refreshToken.substring(0, 12) + "...");
        }


        final Dao dao = new Dao();
        dao.addInviteAndApproveIfUnpaused(
                inviterEmail, invitedEmail, refreshToken);
        inviteSucceeded(xmpp, presence, invitedEmail);
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

    
    private void sendUpdateTime(final Presence presence,
        final XMPPService xmpp, final Map<String, Object> responseJson) {
        log.info("Sending client the next update time.");
        responseJson.put(LanternConstants.UPDATE_TIME,
                LanternControllerConstants.UPDATE_TIME_MILLIS);
        sendResponse(presence, xmpp, responseJson);
    }

    private void processNotInvited(final Presence presence,
        final XMPPService xmpp, final Map<String, Object> responseJson) {
        responseJson.put(LanternConstants.INVITED, Boolean.FALSE);
        log.info("Not allowing uninvited user.");
        sendResponse(presence, xmpp, responseJson);
    }

    private void processClientInfo(final Presence presence,
        final String stats, final String idToUse, final String instanceId,
        final String name, final Mode mode, final String resource,
        final String hostAndPort, final String fallbackHostAndPort,
        final boolean isFallbackProxy) {

        if (StringUtils.isBlank(stats)) {
            log.info("No stats to process!");
            return;
        }
        if (StringUtils.isBlank(instanceId)) {
            log.info("Old client; not tracking stats");
            return;
        }

        log.info("Processing stats!");
        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new MrBeanModule());
        try {
            final Stats data = mapper.readValue(stats, Stats.class);
            // The following will delete the instance if it's not available,
            // updating all counters.
            // (aranhoide: XXX is the comment above still accurate?  I remember
            // at some point available/unavailable events were being handled
            // together up to here, but not anymore.)
            log.info("Setting instance availability");
            final Dao dao = new Dao();
            String countryCode = data.getCountryCode();
            if (StringUtils.isBlank(countryCode)) {
                countryCode = "XX";
            }
            dao.setInstanceAvailable(idToUse, instanceId, countryCode, mode,
                                     resource, hostAndPort, isFallbackProxy);
            if (isFallbackProxy) {
                dao.transitionInstallerLocation(idToUse, instanceId);
            } else {
                dao.processFallbackProxyHostAndPort(
                        idToUse, fallbackHostAndPort);
            }
            try {
                updateStats(data, idToUse, instanceId, name, mode);
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
            final String instanceId, final String name, final Mode mode) {

        final Dao dao = new Dao();

        log.info("Updating stats");
        String countryCode = data.getCountryCode();
        if (StringUtils.isBlank(countryCode)) {
            countryCode = "XX";
        }
        dao.updateUser(idToUse, data.getDirectRequests(),
            data.getDirectBytes(), data.getTotalProxiedRequests(),
            data.getTotalBytesProxied(),
            countryCode, name, mode);
    }
}
