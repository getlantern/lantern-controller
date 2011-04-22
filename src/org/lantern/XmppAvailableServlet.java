package org.lantern;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.lantern.data.Dao;

import com.google.appengine.api.xmpp.Presence;
import com.google.appengine.api.xmpp.XMPPService;
import com.google.appengine.api.xmpp.XMPPServiceFactory;

@SuppressWarnings("serial")
public class XmppAvailableServlet extends HttpServlet {
    
    //private final Logger log = Logger.getLogger(getClass().getName());
    
    @Override
    public void doPost(final HttpServletRequest req, 
        final HttpServletResponse res) throws IOException {
        final XMPPService service = XMPPServiceFactory.getXMPPService();
        final Presence presence = service.parsePresence(req);
        final boolean available = presence.isAvailable();
        final String id = presence.getFromJid().getId();
        System.out.println("Got presence "+available+" for "+id);
        
        final Dao dao = new Dao();
        dao.addUser(id);
        dao.setAvailable(id, available);
        
    }
}