package org.lantern;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
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
        
        if (isInvite(presence)) {
            
            final String from = LanternControllerUtils.userId(presence);
            final Dao dao = new Dao();
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
        
        log.info("Status: '"+presence.getStatus()+"'");
        final String stanza = presence.getStanza();
        log.info("Stanza: "+stanza);
        final String stats = StringUtils.substringBetween(stanza, 
            "<property><name>stats</name><value type=\"string\">", "</value></property>");
        //log.info("Stats JSON: "+stats);
        
        final boolean isGiveMode = 
            LanternControllerUtils.isLantern(presence.getFromJid().getId());
        
        
        final Map<String,Object> responseJson = 
            new LinkedHashMap<String,Object>();
        final String userId = userId(presence, isGiveMode);
        processClientInfo(presence, stats, responseJson, userId);
        if (isGiveMode) {
            processGiveMode(presence, xmpp, available, responseJson);
        } else {
            processGetMode(presence, xmpp, available, responseJson);
        }
    }

    private static final String INVITE = 
        "<property><name>" + LanternConstants.INVITE_KEY + 
        "</name><value type=\"string\">";
    
    
    private static final String LINK = "http://s3.amazonaws.com/lantern/latest.dmg";


    private static final String EMAIL_TOKEN = "{email}";
    
    final String msgContent = 
        "Welcome to Lantern!\n\n" +
        "" +
        "Your trusted friend or contact at e-mail address '"+EMAIL_TOKEN+"' " +
        "has invited you to join the Lantern community. Lantern is a " +
        "network of trusted users who cooperate to provide uncensored " +
        "internet access to people around the world securely. When you " +
        "join Lantern, you become a trusted participant in that network - " +
        "a covenant of sorts dedicated to freedom of expression around the world.\n\n" +
        "" +
        "You can download Lantern at the link below and will then have the " +
        "opportunity to invite people you in turn trust. Remember, don't " +
        "invite just anyone, but please do invite people you trust!\n\n";

    private final String msgBody = 
        msgContent +
        LINK +"\n\n"+
        "-Team Lantern";
    
    private final String msgHtml = 
        msgContent.replaceAll("\n\n", "<br><br>") +
        "<a href='"+LINK+"'>DOWNLOAD LANTERN HERE</a><br><br>"+
        "-Team Lantern";
        
    private void processInvite(final Presence presence) {
        final String stanza = presence.getStanza();
        final String email = StringUtils.substringBetween(stanza, INVITE, 
            "</value></property>");
        final Properties props = new Properties();
        final Session session = Session.getDefaultInstance(props, null);

        final String from = LanternControllerUtils.userId(presence);

        final String body = msgBody.replace(EMAIL_TOKEN, from);
        final String html = msgHtml.replace(EMAIL_TOKEN, from);
        try {
            final javax.mail.Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(from));
            msg.addRecipient(javax.mail.Message.RecipientType.TO,
                new InternetAddress(email));
            msg.setSubject(from + " has invited you to join the Lantern trust network...");
            
            // Unformatted text version
            final MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(body);
            // HTML version
            final MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(html, "text/html");
            htmlPart.setDisposition("inline");
            // Create the Multipart.  Add BodyParts to it.
            final Multipart mp = new MimeMultipart();
            mp.addBodyPart(textPart);
            mp.addBodyPart(htmlPart);
            // Set Multipart as the message's content
            msg.setContent(mp);
            
            Transport.send(msg);
            final Dao dao = new Dao();
            dao.addInvite(from, email);
        } catch (final AddressException e) {
            log.warning("Address error? "+e);
            e.printStackTrace();
        } catch (final MessagingException e) {
            log.warning("Messaging error? "+e);
            e.printStackTrace();
        }
    }

    private boolean isInvite(final Presence presence) {
        final String stanza = presence.getStanza();
        return stanza.contains(INVITE);
    }

    private String userId(final Presence presence, final boolean isGiveMode) {
        if (isGiveMode) {
            return LanternControllerUtils.userId(presence);
        } else {
            // We hash the ID of users in censored countries and just count them
            // as a generic number. We only look at the JID at all to avoid 
            // overcounting.
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

    private void processClientInfo(final Presence presence, 
        final String stats, final Map<String, Object> responseJson, final String idToUse) {
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