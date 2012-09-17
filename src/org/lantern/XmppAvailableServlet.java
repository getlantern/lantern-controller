package org.lantern;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.mrbean.MrBeanModule;
import org.lantern.data.Dao;
import org.littleshoot.util.Sha1Hasher;
import org.littleshoot.util.ThreadUtils;

import com.google.appengine.api.xmpp.Message;
import com.google.appengine.api.xmpp.MessageBuilder;
import com.google.appengine.api.xmpp.MessageType;
import com.google.appengine.api.xmpp.Presence;
import com.google.appengine.api.xmpp.SendResponse;
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

        final Map<String,Object> responseJson = 
                new LinkedHashMap<String,Object>();
        final Dao dao = new Dao();
        final String from = LanternControllerUtils.userId(presence);
        if (!dao.isInvited(from)) {
            log.info(from+" not invited!!");
            processNotInvited(presence, xmpp, responseJson);
            return;
        } else {
            log.info("User is invited");
            responseJson.put(LanternConstants.INVITED, Boolean.TRUE);
        }
        
        if (isInvite(presence)) {
            log.info("Got invite in stanza: "+presence.getStanza());
            if (!dao.hasMoreInvites(from)) {
                log.severe("No more invites for user: "+from);
                return;
            }
            processInvite(presence);
            dao.decrementInvites(from);
            return;
        }
        final boolean available = presence.isAvailable();
        
        
        //final boolean lan = LanternControllerUtils.isLantern(id);
        log.info("Got presence "+available);
        
        final String stats = 
            LanternControllerUtils.getProperty(presence, "stats");
        
        //log.info("Stats JSON: "+stats);
        
        final boolean isGiveMode = 
            LanternControllerUtils.isLantern(presence.getFromJid().getId());
        final String userId = userId(presence, isGiveMode);
        processClientInfo(presence, stats, responseJson, userId);
        if (isGiveMode) {
            processGiveMode(presence, xmpp, available, responseJson);
        } else {
            processGetMode(presence, xmpp, available, responseJson);
        }
    }

    private final class AlreadyInvitedException extends Exception {}

    private void processInvite(final Presence presence) {
        // XXX this is really a jabberid, email template makes it a "mailto:" link
        final String inviterEmail = LanternControllerUtils.userId(presence); 
        final String inviterName;
        final String inviterNameTmp = 
            LanternControllerUtils.getProperty(presence, 
                LanternConstants.INVITER_NAME);
        if (StringUtils.isBlank(inviterNameTmp)) {
            inviterName = inviterEmail;
        } else {
            inviterName = inviterNameTmp;
        }
        final String invitedEmail = 
            LanternControllerUtils.getProperty(presence, 
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
        dao.addInvite(inviterEmail, invitedEmail);
        try {
            MandrillEmailer.sendInvite(inviterName, inviterEmail, invitedEmail);
        } catch (final IOException e) {
            log.warning("Could not send e-mail!\n"+ThreadUtils.dumpStack());
        }
        
    }

    private boolean isInvite(final Presence presence) {
        final String invite = LanternControllerUtils.getProperty(presence, 
            LanternConstants.INVITED_EMAIL);
        if (invite != null) {
            log.info("FOUND INVITE IN: "+presence.getStanza());
        } else {
            log.info("NO INVITE IN: "+presence.getStanza());
        }
        return invite != null;
    }

    private String userId(final Presence presence, final boolean isGiveMode) {
        if (isGiveMode) {
            return LanternControllerUtils.userId(presence);
        } else {
            // We hash the ID of users in censored countries and just count them
            // as a generic number. We only look at the JID at all to avoid 
            // over counting.
            return Sha1Hasher.hash(LanternControllerUtils.userId(presence));
        }
    }

    private void processGetMode(final Presence presence,
        final XMPPService xmpp, final boolean available, 
        final Map<String, Object> responseJson) {
        if (available) {
            // Not we don't tell get mode users to check back in -- we just 
            // give them servers to connect to.
            log.info("Sending servers to available get mode");
            addServers(presence.getFromJid().getId(), responseJson);
            sendResponse(presence, xmpp, responseJson);
        } else {
            log.info("Not sending servers to unavailable clients");
        }
    }

    private void processGiveMode(final Presence presence,
        final XMPPService xmpp, final boolean available, 
        final Map<String, Object> responseJson) {
        if (available) {
            // We always need to tell the client to check back in because 
            // we use it as a fallback for which users are online.
            responseJson.put(LanternConstants.UPDATE_TIME, 
                LanternControllerConstants.UPDATE_TIME_MILLIS);
            log.info("Not sending servers to give mode");
            sendResponse(presence, xmpp, responseJson);
        } else {
            log.info("Not sending servers to unavailable clients");
        }
        
        // The following will delete the instance if it's not available,
        // updating all counters.
        log.info("Setting instance availability");
        final String instanceId = presence.getFromJid().getId();
        final Dao dao = new Dao();
        dao.setInstanceAvailable(instanceId, available);
    }
    

    private void processNotInvited(final Presence presence, 
        final XMPPService xmpp, final Map<String, Object> responseJson) {
        responseJson.put(LanternConstants.INVITED, Boolean.FALSE);
        log.info("Not allowing uninvited user.");
        sendResponse(presence, xmpp, responseJson);
    }

    private void processClientInfo(final Presence presence, 
        final String stats, final Map<String, Object> responseJson, 
        final String idToUse) {
        if (StringUtils.isBlank(stats)) {
            log.info("No stats to process!");
            return;
        }
        log.info("Processing stats!");
        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new MrBeanModule());
        try {
            final Stats data = mapper.readValue(stats, Stats.class);
            addUpdateData(data, responseJson);
            addInviteData(presence, responseJson);
            try {
                updateStats(data, idToUse);
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

    private void addUpdateData(final Stats data,
        final Map<String, Object> responseJson) {
        try {
            final double version = Double.parseDouble(data.getVersion());
            //final double version = 0.001; //just for testing!!
            if (LanternControllerConstants.LATEST_VERSION > version) {
                final Map<String,Object> updateJson = 
                    new LinkedHashMap<String,Object>();
                updateJson.put(LanternConstants.UPDATE_VERSION_KEY, 
                    LanternControllerConstants.LATEST_VERSION);
                updateJson.put(LanternConstants.UPDATE_RELEASED_KEY, 
                    LanternControllerConstants.UPDATE_RELEASE_DATE);
                updateJson.put(LanternConstants.UPDATE_URL_KEY, 
                    LanternControllerConstants.UPDATE_URL);
                updateJson.put(LanternConstants.UPDATE_MESSAGE_KEY, 
                    LanternControllerConstants.UPDATE_MESSAGE);
                responseJson.put(LanternConstants.UPDATE_KEY, updateJson);
            }
        } catch (final NumberFormatException nfe) {
            // Probably running from main line.
            log.info("Format exception on version: "+data.getVersion());
        }
    }

    private void sendResponse(final Presence presence, final XMPPService xmpp, 
        final Map<String, Object> responseJson) {
        final String serversBody = LanternUtils.jsonify(responseJson);
        final Message msg = 
            new MessageBuilder().withRecipientJids(
                presence.getFromJid()).withBody(serversBody).withMessageType(
                    MessageType.HEADLINE).build();
        log.info("Sending response:\n"+responseJson.toString());
        final SendResponse status = xmpp.sendMessage(msg);
        final boolean messageSent = 
            (status.getStatusMap().get(
                presence.getFromJid()) == SendResponse.Status.SUCCESS);
    }

    private void updateStats(final Stats data, final String idToUse) {
        
        final Dao dao = new Dao();
        
        log.info("Updating stats");
        dao.updateUser(idToUse, data.getDirectRequests(), 
            data.getDirectBytes(), data.getTotalProxiedRequests(), 
            data.getTotalBytesProxied(), 
            data.getCountryCode());
    }

    private void addServers(final String jid, 
        final Map<String, Object> responseJson) {

        log.info("Adding servers...");
        final Dao dao = new Dao();
        final Collection<String> servers = dao.getInstances();
        
        // Make sure to remove ourselves.
        servers.remove(jid);
        
        // TODO: We need to provide the same servers for the same users every
        // time. Possibly only provide servers to validated users?
        
        servers.addAll(Arrays.asList("75.101.134.244:7777",
            "laeproxyhr1.appspot.com",
            "rlanternz.appspot.com"));
            
        responseJson.put(LanternConstants.SERVERS, servers);
    }
}