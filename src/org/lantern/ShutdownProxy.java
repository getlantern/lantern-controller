package org.lantern;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.lantern.data.Dao;


@SuppressWarnings("serial")
public class ShutdownProxy extends HttpServlet {

    private static final transient Logger log
        = Logger.getLogger(ShutdownProxy.class.getName());

    //XXX: For testing; disable when deploying to the real controller!
    @Override
    public void doGet(final HttpServletRequest request,
                      final HttpServletResponse response) {
        doPost(request, response);
    }

    @Override
    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response) {
        final String id = request.getParameter(
                LanternControllerConstants.ID_KEY);
        log.info("Requesting shutdown of " + id + "'s proxy.");
        boolean shouldAbort = new Dao().recordProxyShutdown(id);
        if (shouldAbort) {
            log.info("It seems we got a payment at the last minute;"
                     + " aborting shutdown.");
        } else {
            Map<String, Object> m = new HashMap<String, Object>();
            m.put("shutdown-proxy-for", id);
            new SQSUtil().send(m);
            try {
                MandrillEmailer.sendProxyShutdown(id);
            } catch (final IOException e) {
                // We don't raise because at this point the user has received
                // a warning already, and it would be more disruptive if this
                // task was retried and killed a server after the user had
                // resumed paying.
                log.severe(
                    id + " was not warned that their proxy was shut down!");
            }
        }
        LanternControllerUtils.populateOKResponse(response, "OK");
    }
}
