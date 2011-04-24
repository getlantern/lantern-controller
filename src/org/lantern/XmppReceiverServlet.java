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
import com.google.appengine.repackaged.org.json.JSONException;
import com.google.appengine.repackaged.org.json.JSONObject;
import com.google.gdata.util.ServiceException;

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
            final String username = request.getString("un");
            final String pwd = request.getString("pwd");
            // We defer this to make sure we respond to the user as quickly
            // as possible.
            QueueFactory.getDefaultQueue().add(TaskOptions.Builder.withPayload(
                new DeferredTask() {
                @Override
                public void run() {
                    ////log.info("Running deferred task");
                    try {
                        final Dao dao = new Dao();
                        if (ContactsUtil.appearsToBeReal(username, pwd)) {
                            dao.validate(id);
                        }
                    } catch (final IOException e) {
                        e.printStackTrace();
                    } catch (final ServiceException e) {
                        e.printStackTrace();
                    }
                }
            }));
        } catch (final JSONException e) {
        }
        
        final Dao dao = new Dao();
        dao.getUsers();
        final JSONObject response = new JSONObject();
        
        final Collection<String> servers = 
            Arrays.asList("75.101.155.190:7777","racheljohnsonftw.appspot.com",
                "racheljohnsonla.appspot.com");
        try {
            response.put("servers", servers);
            final String serversBody = response.toString();
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