package org.lantern;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.xmpp.JID;
import com.google.appengine.api.xmpp.Message;
import com.google.appengine.api.xmpp.XMPPService;
import com.google.appengine.api.xmpp.XMPPServiceFactory;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.mrbean.MrBeanModule;

import org.lantern.data.Dao;

@SuppressWarnings("serial")
public class XmppReceiverServlet extends HttpServlet {
    
    private final transient Logger log = Logger.getLogger(getClass().getName());

    @Override
    public void doPost(final HttpServletRequest req, 
        final HttpServletResponse res) throws IOException {
        final XMPPService xmpp = XMPPServiceFactory.getXMPPService();
        final Message msg = xmpp.parseMessage(req);
        
        final String fromJid = msg.getFromJid().getId();
        if (!LanternXmppUtils.jidToUserId(fromJid).equals(
                InvitedServerLauncher.INVSRVLAUNCHER_EMAIL)) {
            log.warning("Chat from unauthorized user: " + fromJid);
            return;
        }
        final String body = msg.getBody();
        log.info("Received " + fromJid + " body:\n" + body);

        final ObjectMapper mapper = new ObjectMapper();

        try {
            final Map<String, Object> m = mapper.readValue(
            		body, Map.class);

            /*
             * DRY Warning: The key strings in these messages are not in
             * LanternConstants because they are shared with Python code.
             */

            // Invitee server reports it's up and running.
            final String inviterEmail = (String)m.get("invsrvup-user");
            if (inviterEmail != null) {
                final String installerLocation = (String)m.get("invsrvup-insloc");
                if (installerLocation == null) {
                    log.severe(inviterEmail
                               + " sent invsrv-up with no installer location.");
                    return;
                }
                InvitedServerLauncher.onInvitedServerUp(inviterEmail,
                                                        installerLocation);
                return;
            }

            // New buckets for installers have been created.
            final List<String> bucketList = (List<String>)m.get("register-buckets");
            if (bucketList != null) {
                final Dao dao = new Dao();
                for (String bucketName : bucketList) {
                    dao.addInstallerBucket(bucketName);
                }
                return;
            }

            log.warning(fromJid + " sent us an unknown JSON message: " + body);

        } catch (final JsonParseException e) {
            log.warning("Error parsing chat: "+e.getMessage());
            return;
        } catch (final JsonMappingException e) {
            log.warning("Error parsing chat: "+e.getMessage());
            return;
        } catch (final IOException e) {
            log.warning("Error parsing chat: "+e.getMessage());
            return;
        }
    }
}
