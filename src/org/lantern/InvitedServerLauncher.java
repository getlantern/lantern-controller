package org.lantern;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.lantern.data.Dao;
import org.lantern.data.UnknownUserException;
import org.littleshoot.util.ThreadUtils;

import com.google.appengine.api.xmpp.JID;


public class InvitedServerLauncher {

    private static final transient Logger log = 
        Logger.getLogger(InvitedServerLauncher.class.getName());

    public static final String PENDING = "pending";
    public static final String INVSRVLAUNCHER_EMAIL = "invsrvlauncher@gmail.com";
    private static final JID INVSRVLAUNCHER_JID = new JID(INVSRVLAUNCHER_EMAIL);

    // Contains installers with default fallback servers, for backwards
    // compatibility.
    private static final String
        DEFAULT_INSTALLER_LOCATION = "lantern-installers/fallback";

    public static void sendInvite(final String inviterName,
                                final String inviterEmail,
                                final String refreshToken,
                                final String invitedEmail) {

        final Dao dao = new Dao();

        if (!dao.sendingInvite(inviterEmail, invitedEmail)) {
            log.info("Not re-sending an invite");
            return;
        }

        String installerLocation = dao.getAndSetInstallerLocation(inviterEmail);
        if (installerLocation == null && refreshToken == null) {
            // Inviter is running an old client.
            sendInviteEmail(inviterName, inviterEmail, invitedEmail,
                            DEFAULT_INSTALLER_LOCATION);
        } else if (installerLocation == null && refreshToken != null) {
            // Ask invsrvlauncher to create an instance for this user.
            log.info("Ordering launch of new invited server for "
                     + inviterEmail);
            final String bucket = dao.getAndIncrementLeastUsedBucket();
            if (bucket == null) {
                log.severe("I have no buckets to store installers for invitees!");
                return;
            }

            Map<String, Object> map = new LinkedHashMap<String, Object>();
            /* These aren't in LanternConstants because they are not handled
             * by the client, but by a Python bot.
             * (salt/invsrvlauncher/invsrvlauncher.py at lantern_aws, branch
             * invsrvlauncher)
             */
            map.put("launch-invsrv-as", inviterEmail);
            map.put("launch-refrtok", refreshToken);
            map.put("launch-bucket", bucket);
            new SQSUtil().send(map);
        } else if (!installerLocation.equals(PENDING)) {
            sendInviteEmail(inviterName, inviterEmail, invitedEmail, installerLocation);
        }

        dao.sentInvite(inviterEmail, invitedEmail);
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
        final String[] parts = installerLocation.split("/");
        assert parts.length == 2;
        final String bucket = parts[0];
        final String folder = parts[1];
        final String baseUrl =
            "https://" + bucket + ".s3.amazonaws.com/" + folder
            + "/lantern-" + LanternControllerConstants.LATEST_VERSION;

        try {
            MandrillEmailer.sendInvite(inviterName, inviterEmail, invitedEmail,
                baseUrl + ".dmg", baseUrl + ".exe",
                baseUrl + "-32bit.deb", baseUrl + "-64bit.deb");
        } catch (final IOException e) {
            log.warning("Could not send e-mail!\n"+ThreadUtils.dumpStack());
        }
    }
}
