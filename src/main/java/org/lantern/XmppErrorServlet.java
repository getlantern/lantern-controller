package org.lantern;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.lantern.loggly.LoggerFactory;

@SuppressWarnings("serial")
public class XmppErrorServlet extends HttpServlet {
    
    private final Logger log = LoggerFactory.getLogger(getClass());
    
    @Override
    public void doPost(final HttpServletRequest req, 
        final HttpServletResponse res) throws IOException {
        log.info("Received error: "+req.getRequestURI());
    }
}