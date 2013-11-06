package org.lantern;

import java.io.IOException;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.lantern.data.Dao;
import org.lantern.data.Invite;


@SuppressWarnings("serial")
public class SendInviteTask extends HttpServlet {

    private static final transient Logger log = Logger
            .getLogger(SendInviteTask.class.getName());

    @Override
    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response) {
        String inviterEmail = request.getParameter("inviterEmail");
        String inviteeEmail = request.getParameter("inviteeEmail");
        String description = "invite email from " + inviterEmail
                              + " to " + inviteeEmail;
        log.info("Sending " + description);
        String inviterName = request.getParameter("inviterName");
        if ("null".equals(inviterName)) {
            inviterName = null;
        }
        try {
            MandrillEmailer.sendInvite(
                inviterName,
                inviterEmail,
                inviteeEmail,
                request.getParameter("installerLocation"),
                "true".equals(request.getParameter("inviteeEverSignedIn")));
            new Dao().setInviteStatus(inviterEmail,
                                      inviteeEmail,
                                      Invite.Status.sent);
            LanternControllerUtils.populateOKResponse(response, "OK");
            log.info("Invite task reported success.");
        } catch (IOException e) {
            throw new RuntimeException("Couldn't send " + description
                                       + ":" + e);
        }
    }
}
