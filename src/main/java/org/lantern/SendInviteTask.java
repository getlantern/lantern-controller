package org.lantern;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.lantern.data.Dao;
import org.lantern.data.Invite;
import org.lantern.data.LanternUser;
import org.lantern.loggly.LoggerFactory;
import org.lantern.monitoring.Stats;
import org.lantern.monitoring.Stats.Counters;
import org.lantern.monitoring.StatshubAPI;

@SuppressWarnings("serial")
public class SendInviteTask extends HttpServlet {

    private static final transient Logger log = LoggerFactory
            .getLogger(SendInviteTask.class);

    private static final StatshubAPI STATSHUB = new StatshubAPI();

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
        String configFolder = request.getParameter("configFolder");
        String template = request.getParameter("template");
        // Prevent race conditions when deploying.
        if (template == null) {
            log.warning("No template");
            template = "invite-notification";
        }
        try {
            if (template.equals("invite-notification")) {
                MandrillEmailer.sendInvite(
                        inviterName,
                        inviterEmail,
                        inviteeEmail,
                        configFolder);
            } else if (template.equals("new-trust-network-invite")) {
                MandrillEmailer.sendNewTrustNetworkInvite(
                        inviteeEmail,
                        configFolder);
            } else {
                throw new RuntimeException("Unknown template: " + template);
            }
            new Dao().setInviteStatus(inviterEmail,
                    inviteeEmail,
                    Invite.Status.sent);
            try {
                recordInviteStats(inviterEmail);
            } catch (Exception e) {
                log.log(Level.WARNING,
                        "Unable to record invite stats: " + e.getMessage(), e);
            }
            LanternControllerUtils.populateOKResponse(response, "OK");
            log.info("Invite task reported success.");
        } catch (IOException e) {
            throw new RuntimeException("Couldn't send " + description
                    + ":" + e);
        }
    }

    private void recordInviteStats(final String inviterEmail) throws Exception {
        String inviterGuid = null;
        String country = Stats.UNKNOWN_COUNTRY;
        if ("invite@getlantern.org".equals(inviterEmail)) {
            log.info("Invited by lantern, using email as guid");
            inviterGuid = inviterEmail;
        } else {
            LanternUser inviter = ExportBaselineStats
                    .userWithGuid(inviterEmail);
            if (inviter == null) {
                log.info("Inviter not found, unable to record invite stats");
                return;
            } else {
                log.info("Inviter found, using guid");
                inviterGuid = inviter.getGuid();
                country = ExportBaselineStats.countryForUser(inviter);
            }
        }
        log.info("Recording invite stats for inviter: " + inviterEmail);
        org.lantern.monitoring.Stats stats = new org.lantern.monitoring.Stats();
        stats.setIncrement(Counters.usersInvited, 1);
        STATSHUB.postUserStats(inviterGuid, country, stats);
    }
}
