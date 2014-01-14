package org.lantern;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.xmpp.Message;
import com.google.appengine.api.xmpp.XMPPService;
import com.google.appengine.api.xmpp.XMPPServiceFactory;

@SuppressWarnings("serial")
public class XmppReceiverServlet extends HttpServlet {
    
    private final transient Logger log = org.lantern.loggly.LoggerFactory.getLogger(getClass().getName());

    @Override
    public void doPost(final HttpServletRequest req, 
        final HttpServletResponse res) throws IOException {
        final XMPPService xmpp = XMPPServiceFactory.getXMPPService();
        final Message msg = xmpp.parseMessage(req);
        
        final String fromJid = msg.getFromJid().getId();
        final String body = msg.getBody();
        log.info("Received " + fromJid + " body:\n" + body);
    }
}
