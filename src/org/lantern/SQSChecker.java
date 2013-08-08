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
         * DRY Warning: The key strings in these messages are not in
         * LanternConstants because they are shared with Python code.
         */
        // Fallback proxy reports it's up and running.
        final String userId = (String)msg.get("invsrvup-user");
        if (userId != null) {
            final String installerLocation = (String)msg.get(
                    "invsrvup-insloc");
            if (installerLocation == null) {
                log.severe(userId
                           + " sent invsrv-up with no installer location.");
                return;
            }
            final String status = (String)msg.get("invsrvup-status");
            if (status == null) {
                log.severe(userId + " sent invsrv-up with no status.");
            }
            InvitedServerLauncher.onFallbackProxyUp(userId,
                                                    installerLocation,
                                                    status);
            return;
        }
        log.warning("I don't understand this message: " + msg);
    }
}
