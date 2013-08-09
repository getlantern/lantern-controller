package org.lantern;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.lantern.data.Dao;
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
                                final String invitedEmail,
                                boolean noCost) {

        final Dao dao = new Dao();

        if (!dao.sendingInvite(inviterEmail, invitedEmail, noCost)) {
            log.info("Not re-sending an invite");
            return;
        }

        String installerLocation =
            dao.getAndSetInstallerLocation(inviterEmail, PENDING);
        if (installerLocation == null && refreshToken == null) {
            // Inviter is running an old client.
            sendInviteEmail(inviterName, inviterEmail, invitedEmail,
                            DEFAULT_INSTALLER_LOCATION);
        } else if (installerLocation == null && refreshToken != null) {
            // Ask invsrvlauncher to create an instance for this user.
            log.info("Ordering launch of new invited server for "
                     + inviterEmail);
            orderServerLaunch(inviterEmail, refreshToken);
        } else if (!installerLocation.equals(PENDING)) {
            sendInviteEmail(inviterName, inviterEmail, invitedEmail, installerLocation);
        }

        dao.sentInvite(inviterEmail, invitedEmail);
    }

    public static void orderServerLaunch(final String userid,
                                         final String refreshToken) {
        Map<String, Object> map = new HashMap<String, Object>();
        /* These aren't in LanternConstants because they are not handled
         * by the client, but by a Python bot.
         * (lantern_aws/salt/cloudmaster/cloudmaster.py)
         */
        map.put("launch-invsrv-as", userid);
        map.put("launch-refrtok", refreshToken);
        new SQSUtil().send(map);
    }

    public static void onFallbackProxyUp(final String userId,
                                         final String installerLocation,
                                         final String status) {
        final Dao dao = new Dao();
        if (status.equals("awaiting_token")) {
            boolean hasToken = dao.setInstallerLocationAndCheckToken(
                                                    userId, installerLocation);
            if (!hasToken) {
                sendTokenRequestEmail(userId, installerLocation);
            }
        } else if (status.equals("setup_complete")) {
            try {
                final Collection<String> invitees
                    = dao.setInstallerLocationAndGetInvitees(
                        userId, installerLocation);
                for (String invitedEmail : invitees) {
                    sendInviteEmail(userId, userId, invitedEmail,
                                    installerLocation);
                }
            } catch (final UnknownUserException e) {
                log.severe("Server up for unknown inviter " + userId);
            }
        } else {
            log.severe("Unknown status: " + status);
        }
    }

    private static void sendInviteEmail(final String inviterName,
                                        final String inviterEmail,
                                        final String invitedEmail,
                                        final String installerLocation) {

        try {
            Map<String, String> m = parseInstallerLocation(installerLocation);
            MandrillEmailer.sendInvite(inviterName, inviterEmail, invitedEmail,
                                       m.get("osx"), m.get("windows"),
                                       m.get("linux"));
        } catch (final IOException e) {
            log.warning("Could not send invite e-mail!\n"
                        + ThreadUtils.dumpStack());
        }
    }

    private static void sendTokenRequestEmail(final String userId,
                                              final String installerLocation) {
        try {
            Map<String, String> m = parseInstallerLocation(installerLocation);
            MandrillEmailer.sendTokenRequest(userId, m.get("osx"),
                                             m.get("windows"), m.get("linux"));
        } catch (final IOException e) {
            log.warning("Could not send token request e-mail!\n"
                        + ThreadUtils.dumpStack());
        }
    }

    private static Map<String, String> parseInstallerLocation(
                                            final String installerLocation) {
        final String[] parts = installerLocation.split(",");
        assert parts.length == 2;
        final String folder = parts[0];
        final String version = parts[1];
        final String baseUrl =
            "https://s3.amazonaws.com/" + folder + "/lantern-net-installer_";
        Map<String, String> m = new HashMap<String, String>();
        m.put("osx", baseUrl + "macos_" + version + ".dmg");
        m.put("windows", baseUrl + "windows_" + version + ".exe");
        m.put("linux", baseUrl + "unix_" + version + ".sh");
        return m;
    }
}
