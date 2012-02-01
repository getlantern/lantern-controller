package org.lantern;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.lantern.data.Dao;

import com.google.appengine.api.taskqueue.DeferredTask;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.xmpp.Message;
import com.google.appengine.api.xmpp.MessageBuilder;
import com.google.appengine.api.xmpp.MessageType;
import com.google.appengine.api.xmpp.Presence;
import com.google.appengine.api.xmpp.SendResponse;
import com.google.appengine.api.xmpp.XMPPService;
import com.google.appengine.api.xmpp.XMPPServiceFactory;

@SuppressWarnings("serial")
public class XmppAvailableServlet extends HttpServlet {
    
    //private final Logger log = Logger.getLogger(getClass().getName());
    
    @Override
    public void doPost(final HttpServletRequest req, 
        final HttpServletResponse res) throws IOException {
        final XMPPService xmpp = XMPPServiceFactory.getXMPPService();
        final Presence presence = xmpp.parsePresence(req);
        final boolean available = presence.isAvailable();
        final String id = presence.getFromJid().getId();
        System.out.println("XmppAvailableServlet::Got presence "+available+" for "+id);
        
        System.out.println("Status: '"+presence.getStatus()+"'");
        final String stanza = presence.getStanza();
        System.out.println("Stanza: "+stanza);
        final String stats = StringUtils.substringBetween(stanza, 
                "<property><name>stats</name><value type=\"string\">", "</value></property>");
        System.out.println("Stats JSON: "+stats);
        updateStats(stats, presence, xmpp);
        if (LanternControllerUtils.isLantern(id)) {
            final Dao dao = new Dao();
            // The following will delete the instance if it's not available,
            // updating all counters.
            dao.setInstanceAvailable(id, available);
            
        } else {
            System.out.println("XmppAvailableServlet::Not a Lantern ID: "+id);
        }
    }

    private void updateStats(final String stats, final Presence presence, 
        final XMPPService xmpp) throws IOException {
        if (StringUtils.isBlank(stats)) {
            System.out.println("No stats!");
            return;
        }
        final String jid = presence.getFromJid().getId();
        final Map<String,Object> responseJson = 
            new LinkedHashMap<String,Object>();
        //final JSONObject responseJson = new JSONObject();
        final ObjectMapper mapper = new ObjectMapper();
        final Map<String,Object> request = mapper.readValue(stats, Map.class);
        //final JSONObject request = new JSONObject(stats);
        final long directRequests = 
            (Integer) request.get(LanternConstants.DIRECT_REQUESTS);
        final long directBytes = 
            (Integer) request.get(LanternConstants.DIRECT_BYTES);
        
        final long requestsProxied = 
            (Integer) request.get(LanternConstants.REQUESTS_PROXIED);
        final long bytesProxied = 
            (Integer) request.get(LanternConstants.BYTES_PROXIED);
        
        //final String machineId = request.getString("m");
        final String countryCode = 
            (String) request.get(LanternConstants.COUNTRY_CODE);
        //final Map<String,Object> whitelistAdditionsJson = 
        //    request.get(LanternConstants.WHITELIST_ADDITIONS);
        //final Map<String,Object>  whitelistRemovalsJson = 
        //    request.get(LanternConstants.WHITELIST_REMOVALS);
        
        final Collection<String> whitelistAdditions =
            (Collection<String>) request.get(LanternConstants.WHITELIST_ADDITIONS);
            //LanternUtils.toCollection(whitelistAdditionsJson);
        
        final Collection<String> whitelistRemovals =
            (Collection<String>) request.get(LanternConstants.WHITELIST_REMOVALS);
            //LanternUtils.toCollection(whitelistRemovalsJson);
        
        final String versionString = 
            (String) request.get(LanternConstants.VERSION_KEY);
        
        try {
            final double version = Double.parseDouble(versionString);
            if (LanternConstants.LATEST_VERSION > version) {
                final Map<String,Object> updateJson = 
                    new LinkedHashMap<String,Object>();
                updateJson.put(LanternConstants.UPDATE_VERSION_KEY, 
                    LanternConstants.LATEST_VERSION);
                updateJson.put(LanternConstants.UPDATE_RELEASED_KEY, 
                    LanternConstants.UPDATE_RELEASE_DATE);
                updateJson.put(LanternConstants.UPDATE_URLS_KEY, 
                        LanternConstants.UPDATE_URLS);
                updateJson.put(LanternConstants.UPDATE_MESSAGE_KEY, 
                    LanternConstants.UPDATE_MESSAGE);
                responseJson.put(LanternConstants.UPDATE_KEY, updateJson);
            }
        } catch (final NumberFormatException nfe) {
            // Probably running from main line.
            System.out.println("Format exception on version: "+versionString);
        }

        System.out.println("About to queue task...");
        // We defer this to make sure we respond to the user as quickly
        // as possible.
        QueueFactory.getDefaultQueue().add(TaskOptions.Builder.withPayload(
            new DeferredTask() {
            @Override
            public void run() {
                ////log.info("Running deferred task");
                final Dao dao = new Dao();
                System.out.println("Setting instance to available");
                dao.setInstanceAvailable(jid, true);
                
                System.out.println("Updating stats");
                dao.updateUser(jid, directRequests, 
                    directBytes, requestsProxied, bytesProxied, 
                    countryCode);

                dao.whitelistAdditions(whitelistAdditions, countryCode);
                dao.whitelistRemovals(whitelistRemovals, countryCode);
            }
        }));

        

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
        responseJson.put(LanternConstants.UPDATE_TIME, 
            LanternConstants.UPDATE_TIME_MILLIS);
        final String serversBody = LanternUtils.jsonify(responseJson);
        final Message msg = 
            new MessageBuilder().withRecipientJids(
                presence.getFromJid()).withBody(serversBody).withMessageType(
                    MessageType.HEADLINE).build();
        System.out.println("Sending response:\n"+responseJson.toString());
        final SendResponse status = xmpp.sendMessage(msg);
        final boolean messageSent = 
            (status.getStatusMap().get(
                presence.getFromJid()) == SendResponse.Status.SUCCESS);

    }
}