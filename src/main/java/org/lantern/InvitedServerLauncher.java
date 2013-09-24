package org.lantern;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.lantern.data.Dao;
import org.lantern.data.LanternUser;
import org.lantern.data.UnknownUserException;
import org.littleshoot.util.ThreadUtils;


public class InvitedServerLauncher {

    private static final transient Logger log = 
        Logger.getLogger(InvitedServerLauncher.class.getName());

    public static final String PENDING = "pending";

    // Contains installers with default fallback servers, for backwards
    // compatibility.
    private static final String
        DEFAULT_INSTALLER_LOCATION = "lantern-installers/fallback,default";

    public static void sendInvite(final String inviterName,
                                  final String inviterEmail,
                                  final String refreshToken,
                                  final String invitedEmail) {

        final Dao dao = new Dao();

        String installerLocation = dao.getAndSetInstallerLocation(inviterEmail);
        if (installerLocation == null && refreshToken == null) {
            // Inviter is running an old client.
            sendInviteEmail(inviterName, inviterEmail, invitedEmail,
                            DEFAULT_INSTALLER_LOCATION);
        } else if (installerLocation == null && refreshToken != null) {
            // Ask invsrvlauncher to create an instance for this user.
            log.info("Ordering launch of new invited server for "
                     + inviterEmail);
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            /* These aren't in LanternConstants because they are not handled
             * by the client, but by a Python bot.
             * (salt/invsrvlauncher/invsrvlauncher.py at lantern_aws, branch
             * invsrvlauncher)
             */
            map.put("launch-invsrv-as", inviterEmail);
            map.put("launch-refrtok", refreshToken);
            new SQSUtil().send(map);
        } else if (!installerLocation.equals(PENDING)) {
            sendInviteEmail(inviterName, inviterEmail, invitedEmail, installerLocation);
        } else {
            log.info("Installer location is pending -- not sending invite");
        }
    }

    public static void onInvitedServerUp(final String inviterEmail, 
                                         final String installerLocation) {
        final Dao dao = new Dao();
        try {
            final Collection<String> invitees = dao.setInstallerLocationAndGetInvitees(inviterEmail, installerLocation);
            for (String invitedEmail : invitees) {
                sendInviteEmail(inviterEmail, inviterEmail, invitedEmail, installerLocation);
            }
        } catch (final UnknownUserException e) {
            log.severe("Server up for unknown inviter " + inviterEmail);
        }
    }

    private static void sendInviteEmail(final String inviterName,
                                        final String inviterEmail,
                                        final String invitedEmail,
                                        final String installerLocation) {
        final Dao dao = new Dao();
        if (!dao.shouldSendInvite(inviterEmail, invitedEmail)) {
            log.info("Not re-sending an invite");
            return;
        }
        final String[] parts = installerLocation.split(",");
        assert parts.length == 2;
        final String folder = parts[0];
        final String version = parts[1];
        final String baseUrl =
            "https://s3.amazonaws.com/" + folder + "/lantern-net-installer_";

        //check if the invitee is already a user
        LanternUser user = dao.getUser(invitedEmail);

        try {
            MandrillEmailer.sendInvite(inviterName, inviterEmail, invitedEmail,
                baseUrl + "macos_" + version + ".dmg",
                baseUrl + "windows_" + version + ".exe",
                baseUrl + "unix_" + version + ".sh", user.isEverSignedIn());
            dao.sentInvite(inviterEmail, invitedEmail);
        } catch (final IOException e) {
            log.warning("Could not send e-mail!\n"+ThreadUtils.dumpStack());
        }
    }
}
