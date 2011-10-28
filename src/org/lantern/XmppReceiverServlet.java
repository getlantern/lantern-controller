package org.lantern;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.lantern.data.Dao;

import com.google.appengine.api.taskqueue.DeferredTask;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.xmpp.JID;
import com.google.appengine.api.xmpp.Message;
import com.google.appengine.api.xmpp.MessageBuilder;
import com.google.appengine.api.xmpp.MessageType;
import com.google.appengine.api.xmpp.SendResponse;
import com.google.appengine.api.xmpp.XMPPService;
import com.google.appengine.api.xmpp.XMPPServiceFactory;
import com.google.appengine.repackaged.org.json.JSONArray;
import com.google.appengine.repackaged.org.json.JSONException;
import com.google.appengine.repackaged.org.json.JSONObject;

@SuppressWarnings("serial")
public class XmppReceiverServlet extends HttpServlet {
    
    //private final Logger log = Logger.getLogger(getClass().getName());
    
    @Override
    public void doPost(final HttpServletRequest req, 
        final HttpServletResponse res) throws IOException {
        final XMPPService xmpp = XMPPServiceFactory.getXMPPService();
        final Message message;
        try {
            message = xmpp.parseMessage(req);
        } catch (final IllegalArgumentException e) {
            System.err.println("Caught exception processing request URI: "+
                req.getRequestURI());
            System.err.println("Headers:\n"+toHeaders(req));
            System.err.println("Body:\n"+
                new String(toByteArray(req.getInputStream()), "UTF-8"));
            e.printStackTrace();
            return;
        }
        System.out.println("Got message from: "+message.getFromJid().getId());
        
        final String body = message.getBody();
        
        final String jid = message.getFromJid().getId();
        
        // If it's a new user, send it an invitation to subscribe 
        // to our status.
        System.out.println("Sending invitation to "+jid);
        xmpp.sendInvitation(new JID(jid));
        
        //dao.addUser(id);
        // TODO: Decode the message. We want to make sure the message is 
        // always encoded with our public key.

        final JSONObject responseJson = new JSONObject();
        try {
            final JSONObject request = new JSONObject(body);
            //final String username = 
            //    request.getString(LanternConstants.USER_NAME);
            //final String pwd = request.getString(LanternConstants.PASSWORD);
            final long directRequests = 
                request.getLong(LanternConstants.DIRECT_REQUESTS);
            final long directBytes = 
                request.getLong(LanternConstants.DIRECT_BYTES);
            
            final long requestsProxied = 
                request.getLong(LanternConstants.REQUESTS_PROXIED);
            final long bytesProxied = 
                request.getLong(LanternConstants.BYTES_PROXIED);
            
            //final String machineId = request.getString("m");
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
            System.err.println("JSON error! "+e.getMessage()+Arrays.asList(e.getStackTrace()));
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
                    message.getFromJid()).withBody(serversBody).withMessageType(
                        MessageType.NORMAL).build();
            System.out.println("Sending response:\n"+responseJson.toString());
            final SendResponse status = xmpp.sendMessage(msg);
            final boolean messageSent = 
                (status.getStatusMap().get(
                    message.getFromJid()) == SendResponse.Status.SUCCESS);
        } catch (final JSONException e) {
            e.printStackTrace();
        }
    }

    private String toHeaders(final HttpServletRequest req) {
        final StringBuilder sb = new StringBuilder();
        final List<String> headerNames = Collections.list(req.getHeaderNames());
        for (final String hn : headerNames) {
            final String val = req.getHeader(hn);
            sb.append(hn);
            sb.append(";");
            sb.append(val);
            sb.append("\n");
        }
        return sb.toString();
    }
    
    private static byte[] toByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        copy(input, output);
        return output.toByteArray();
    }
    
    public static int copy(InputStream input, OutputStream output) throws IOException {
        long count = copyLarge(input, output);
        if (count > Integer.MAX_VALUE) {
            return -1;
        }
        return (int) count;
    }
    
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
    public static long copyLarge(InputStream input, OutputStream output)
            throws IOException {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        long count = 0;
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }
}
