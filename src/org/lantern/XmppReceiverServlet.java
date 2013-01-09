package org.lantern;

import java.io.IOException;
import java.util.logging.Logger;
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

@SuppressWarnings("serial")
public class XmppReceiverServlet extends HttpServlet {
    
    private final transient Logger log = Logger.getLogger(getClass().getName());

    @Override
    public void doPost(final HttpServletRequest req, 
        final HttpServletResponse res) throws IOException {
        final XMPPService xmpp = XMPPServiceFactory.getXMPPService();
        final Message msg = xmpp.parseMessage(req);
        
        final JID fromJid = msg.getFromJid();
        final String body = msg.getBody();
        log.info("Received "+fromJid+" body:\n"+body);

        final ObjectMapper mapper = new ObjectMapper();
        try {
            final Map<String, Object> m = mapper.readValue(
            		body, Map.class);
            final String inviterEmail = (String)m.get("invsrvup-user");
            if (inviterEmail == null) {
                log.warning("Got JSON message with no inviter email.");
                return;
            }
            final String address = (String)m.get("invsrvup-address");
            if (address == null) {
                log.severe(inviterEmail + " sent invsrv-up with no address.");
                return;
            }
            InvitedServerLauncher.onInvitedServerUp(inviterEmail, address);
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
