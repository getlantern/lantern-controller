package org.lantern;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
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
import com.google.appengine.repackaged.org.json.JSONArray;
import com.google.appengine.repackaged.org.json.JSONException;
import com.google.appengine.repackaged.org.json.JSONObject;

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
        final XMPPService xmpp) {
        if (StringUtils.isBlank(stats)) {
            System.out.println("No stats!");
            return;
        }
        final String jid = presence.getFromJid().getId();
        final JSONObject responseJson = new JSONObject();
        try {
            final JSONObject request = new JSONObject(stats);
            final long directRequests = 
                request.getLong(LanternConstants.DIRECT_REQUESTS);
            final long directBytes = 
                request.getLong(LanternConstants.DIRECT_BYTES);
            
            final long requestsProxied = 
                request.getLong(LanternConstants.REQUESTS_PROXIED);
            final long bytesProxied = 
                request.getLong(LanternConstants.BYTES_PROXIED);
            
            final String countryCode = 
                request.getString(LanternConstants.COUNTRY_CODE);
            final JSONArray whitelistAdditionsJson = 
                request.getJSONArray(LanternConstants.WHITELIST_ADDITIONS);
            final JSONArray whitelistRemovalsJson = 
                request.getJSONArray(LanternConstants.WHITELIST_REMOVALS);
            
            final Collection<String> whitelistAdditions =
                LanternUtils.toCollection(whitelistAdditionsJson);
            
            final Collection<String> whitelistRemovals =
                LanternUtils.toCollection(whitelistRemovalsJson);
            
            final String versionString = 
                request.getString(LanternConstants.VERSION_KEY);

            try {
                final double version = Double.parseDouble(versionString);
                if (LanternConstants.LATEST_VERSION > version) {
                    final JSONObject updateJson = new JSONObject();
                    updateJson.put(LanternConstants.UPDATE_TITLE_KEY, 
                        LanternConstants.UPDATE_TITLE);
                    updateJson.put(LanternConstants.UPDATE_MESSAGE_KEY, 
                        LanternConstants.UPDATE_MESSAGE);
                    updateJson.put(LanternConstants.UPDATE_VERSION_KEY, 
                        String.valueOf(LanternConstants.LATEST_VERSION));
                    updateJson.put(LanternConstants.UPDATE_URL_KEY,
                        LanternConstants.UPDATE_URL);
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
        } catch (final JSONException e) {
            System.out.println("JSON Error");
            e.printStackTrace();
        }
        

        final Dao dao = new Dao();
        final Collection<String> servers = dao.getInstances();
        
        // Make sure to remove ourselves.
        servers.remove(jid);
        
        // TODO: We need to provide the same servers for the same users every
        // time. Possibly only provide servers to validated users?
        servers.addAll(Arrays.asList("75.101.134.244:7777",
            "laeproxyhr1.appspot.com",
            "rlanternz.appspot.com"));
        try {
            responseJson.put(LanternConstants.SERVERS, servers);
            responseJson.put(LanternConstants.UPDATE_TIME, 
                LanternConstants.UPDATE_TIME_MILLIS);
            final String serversBody = responseJson.toString();
            final Message msg = 
                new MessageBuilder().withRecipientJids(
                    presence.getFromJid()).withBody(serversBody).withMessageType(
                        MessageType.HEADLINE).build();
            System.out.println("Sending response:\n"+responseJson.toString());
            final SendResponse status = xmpp.sendMessage(msg);
            final boolean messageSent = 
                (status.getStatusMap().get(
                    presence.getFromJid()) == SendResponse.Status.SUCCESS);
        } catch (final JSONException e) {
            e.printStackTrace();
        }
    }
}