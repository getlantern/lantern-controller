package org.lantern;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import org.lantern.data.Dao;

@SuppressWarnings("serial")
public class SQSChecker extends HttpServlet {
    private static final transient Logger log = Logger
            .getLogger(SQSChecker.class.getName());

    @Override
    public void doGet(final HttpServletRequest request,
            final HttpServletResponse response) {
        List<Map<String, Object>> messages = new SQSUtil().receive();
        for (Map<String, Object> msg : messages) {
            handleMessage(msg);
        }
        LanternControllerUtils.populateOKResponse(response, "OK");
    }

    private void handleMessage(final Map<String, Object> msg) {
        /*
         * DRY Warning: The key strings in these messages are not constants
         * because they are shared with Python code (just grep for them in
         * the lantern_aws project source).
         */
        // Fallback proxy reports it's up and running.
        String inviterEmail = (String)msg.get("fp-up-user");
        if (inviterEmail == null) {
            log.severe("I don't understand this message: " + msg);
            return;
        }
        String instanceId = (String)msg.get("fp-up-instance");
        if (instanceId == null) {
            log.severe(inviterEmail + " sent fp-up with no instance ID.");
            return;
        }
        String installerLocation = (String)msg.get("fp-up-insloc");
        if (installerLocation == null) {
            log.severe(inviterEmail
                       + " sent fp-up with no installer location.");
            return;
        }
        String ip = (String)msg.get("fp-up-ip");
        if (ip == null) {
            log.severe(inviterEmail
                       + " sent fp-up with no ip.");
            return;
        }
        String port = (String)msg.get("fp-up-port");
        if (port == null) {
            log.severe(inviterEmail
                       + " sent fp-up with no port.");
            return;
        }
        FallbackProxyLauncher.onFallbackProxyUp(inviterEmail,
                                                instanceId,
                                                installerLocation,
                                                ip,
                                                port);
    }
}
