package org.lantern;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.xmpp.Presence;
import com.google.appengine.api.xmpp.XMPPService;
import com.google.appengine.api.xmpp.XMPPServiceFactory;

@SuppressWarnings("serial")
public class XmppProbeServlet extends HttpServlet {
    
    //private final Logger log = Logger.getLogger(getClass().getName());
    
    @Override
    public void doPost(final HttpServletRequest req, 
        final HttpServletResponse res) throws IOException {
        final XMPPService service = XMPPServiceFactory.getXMPPService();
        final Presence presence = service.parsePresence(req);
        System.out.println("XmppProbeServlet::presence: "+presence);
        System.out.println("XmppProbeServlet::stanza"+presence.getStanza());
        System.out.println("XmppProbeServlet::status: "+presence.getStatus());
        System.out.println("XmppProbeServlet::show: "+presence.getPresenceShow());
        System.out.println("XmppProbeServlet::type: "+presence.getPresenceType());
    }
}