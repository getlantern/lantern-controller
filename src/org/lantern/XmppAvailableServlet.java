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
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.mrbean.MrBeanModule;
import org.lantern.data.Dao;

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
        final boolean available = presence.isAvailable();
        
        
        final String id = presence.getFromJid().getId();
        log.info("ID: "+id);
        //final boolean lan = LanternControllerUtils.isLantern(id);
        log.info("Got presence "+available+" for "+id);
        
        log.info("Status: '"+presence.getStatus()+"'");
        final String stanza = presence.getStanza();
        log.info("Stanza: "+stanza);
        final String stats = StringUtils.substringBetween(stanza, 
            "<property><name>stats</name><value type=\"string\">", "</value></property>");
        //log.info("Stats JSON: "+stats);
        
        final boolean isGiveMode = LanternControllerUtils.isLantern(id);
        
        final Map<String,Object> responseJson = 
            new LinkedHashMap<String,Object>();
        try {
            updateStats(stats, presence, responseJson);
        } catch (final IOException e) {
            log.severe("Error updating stats: "+e.getMessage());
        } catch (final UnsupportedOperationException e) {
            log.severe("Error updating stats: "+e.getMessage());
        }
        
        // We always need to tell the client to check back in because we use
        // it as a fallback for which users are online.
        if (available) {
            if (isGiveMode) {
                responseJson.put(LanternConstants.UPDATE_TIME, 
                    LanternConstants.UPDATE_TIME_MILLIS);
                log.info("Not sending servers to give mode");
            } else {
                log.info("Sending servers to available get mode");
                addServers(id, responseJson);
            }
            sendResponse(presence, xmpp, responseJson);
        } else {
            log.info("Not sending servers to unavailable clients");
        }
        final Dao dao = new Dao();
        // The following will delete the instance if it's not available,
        // updating all counters.
        dao.setInstanceAvailable(id, available);
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

    private void updateStats(final String stats, final Presence presence, 
        final Map<String, Object> responseJson) throws IOException {
        final String jid = presence.getFromJid().getId();
        if (StringUtils.isBlank(stats)) {
            log.info("No stats!");
            return;
        }
        
        //final JSONObject responseJson = new JSONObject();
        //final ObjectMapper mapper = new ObjectMapper(new SmileFactory());
        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new MrBeanModule());
        final Stats read = mapper.readValue(stats, Stats.class);
        try {
            final double version = Double.parseDouble(read.getVersion());
            //final double version = 0.001; //just for testing!!
            if (LanternConstants.LATEST_VERSION > version) {
                final Map<String,Object> updateJson = 
                    new LinkedHashMap<String,Object>();
                updateJson.put(LanternConstants.UPDATE_VERSION_KEY, 
                    LanternConstants.LATEST_VERSION);
                updateJson.put(LanternConstants.UPDATE_RELEASED_KEY, 
                    LanternConstants.UPDATE_RELEASE_DATE);
                updateJson.put(LanternConstants.UPDATE_URL_KEY, 
                        LanternConstants.UPDATE_URL);
                updateJson.put(LanternConstants.UPDATE_MESSAGE_KEY, 
                    LanternConstants.UPDATE_MESSAGE);
                responseJson.put(LanternConstants.UPDATE_KEY, updateJson);
            }
        } catch (final NumberFormatException nfe) {
            // Probably running from main line.
            log.info("Format exception on version: "+read.getVersion());
        }

        final Dao dao = new Dao();
        log.info("Setting instance to available");
        dao.setInstanceAvailable(jid, true);
        
        log.info("Updating stats");
        dao.updateUser(jid, read.getDirectRequests(), 
            read.getDirectBytes(), read.getTotalProxiedRequests(), 
            read.getTotalBytesProxied(), 
            read.getCountryCode());
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