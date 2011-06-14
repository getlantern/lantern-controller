package org.lantern;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

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
        final Message message = xmpp.parseMessage(req);

        final String id = message.getFromJid().getId();
        //log.info("Got XMPP message from "+fromJid);
        final String body = message.getBody();
        
        final String from = message.getFromJid().getId().split("/")[0];
        System.out.println("Sending invitation to "+from);
        xmpp.sendInvitation(new JID(from));
        
        //dao.addUser(id);
        // TODO: Decode the message. We want to make sure the message is 
        // always encoded with our public key.

        try {
            final JSONObject request = new JSONObject(body);
            final String username = request.getString(LanternConstants.USER_NAME);
            final String pwd = request.getString(LanternConstants.PASSWORD);
            final long directRequests = request.getLong(LanternConstants.DIRECT_REQUESTS);
            final long directBytes = request.getLong(LanternConstants.DIRECT_BYTES);
            
            final long requestsProxied = request.getLong(LanternConstants.REQUESTS_PROXIED);
            final long bytesProxied = request.getLong(LanternConstants.BYTES_PROXIED);
            
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

            System.out.println("About to queue task...");
            // We defer this to make sure we respond to the user as quickly
            // as possible.
            QueueFactory.getDefaultQueue().add(TaskOptions.Builder.withPayload(
                new DeferredTask() {
                @Override
                public void run() {
                    ////log.info("Running deferred task");
                    final Dao dao = new Dao();
                    System.out.println("Updating stats");
                    dao.updateUser(from, username, pwd, directRequests, 
                        directBytes, requestsProxied, bytesProxied, 
                        countryCode);

                    dao.whitelistAdditions(whitelistAdditions, countryCode);
                    dao.whitelistRemovals(whitelistRemovals, countryCode);
                    /*
                    if (!user.isValidated()) {
                        if (ContactsUtil.appearsToBeReal(username, pwd)) {
                            dao.validate(user);
                        }
                    }
                    */
                }
            }));
        } catch (final JSONException e) {
            System.err.println("JSON error! "+e.getMessage()+Arrays.asList(e.getStackTrace()));
            e.printStackTrace();
        }
        
        final Dao dao = new Dao();
        final Collection<String> validated = dao.getUsers();
        final JSONObject json = new JSONObject();
        
        // TODO: We need to provide the same servers for the same users every
        // time. Possibly only provide servers to validated users?
        final Collection<String> servers = 
            Arrays.asList("75.101.134.244:7777","racheljohnsonftw.appspot.com",
            //Arrays.asList("75.101.155.190:7777","racheljohnsonftw.appspot.com",
                "racheljohnsonla.appspot.com");
        try {
            json.put("servers", servers);
            json.put("validated", validated);
            final String serversBody = json.toString();
            final Message msg = 
                new MessageBuilder().withRecipientJids(message.getFromJid()).withBody(serversBody).build();
            final SendResponse status = xmpp.sendMessage(msg);
            final boolean messageSent = 
                (status.getStatusMap().get(
                    message.getFromJid()) == SendResponse.Status.SUCCESS);
        } catch (final JSONException e) {
            e.printStackTrace();
        }
    }
}