package org.lantern;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.lantern.loggly.LoggerFactory;


@SuppressWarnings("serial")
public class SendUpdateTask extends HttpServlet {

    private static final transient Logger log = LoggerFactory
            .getLogger(SendUpdateTask.class);

    @Override
    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response) {
        String toEmail = request.getParameter("toEmail");
        String description = "send update notification to " + toEmail;
        log.info(description);
        try {
            MandrillEmailer.sendVersionUpdate(
                toEmail,
                request.getParameter("version"),
                request.getParameter("installerLocation"));
            LanternControllerUtils.populateOKResponse(response, "OK");
            log.info("Update notification task reported success.");
        } catch (IOException e) {
            throw new RuntimeException("Couldn't " + description
                                       + ":" + e);
        }
    }
}
