package org.lantern;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.xmpp.XMPPService;
import com.google.appengine.api.xmpp.XMPPServiceFactory;

@SuppressWarnings("serial")
public class XmppSubscribeServlet extends HttpServlet {
    
    @Override
    public void doPost(final HttpServletRequest req, 
        final HttpServletResponse res) throws IOException {
        System.out.println("SUBSCRIBE!!!");
        final XMPPService xmppService = XMPPServiceFactory.getXMPPService();
        //final Subscription sub = xmppService.parseSubscription(req);

        // Split the bare XMPP address (e.g., user@gmail.com)
        // from the resource (e.g., gmail.CD6EBC4A)
        //final String from = sub.getFromJid().getId().split("/")[0];
        //System.out.println("Got subscribe from: "+from);
    }
}