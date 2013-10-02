package org.lantern;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.lantern.data.Dao;
import org.lantern.data.Invite;
import org.lantern.data.LanternUser;
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
                                  final String invitedEmail) {

        final Dao dao = new Dao();
        // An invite only gets to this point once it has been authorized, so
        // this is a good place to make sure we record that fact.
        dao.setInviteStatus(inviterEmail,
                            invitedEmail,
                            Invite.Status.authorized);

        String fpuid = dao.getUser(inviterEmail).getFallbackProxyUserId();
        if (fpuid == null) {
            log.warning("Old invite; we'll process this when the user gets"
                        + " a fallbackProxyUserId");
            return;
        }
        String refreshToken = dao.getUser(fpuid).getRefreshToken();
        if (refreshToken == null) {
            throw new RuntimeException(
                    "Fallback proxy user without refresh token?" + fpuid);
        }
        // TODO: instead of fetching using a hardcoded instanceid, pass the real one here
        log.info("Maximum client count from Librato: " + Librato.getMaximumClientCountForProxyInLastMonth("429e523560d0f39949843833f05c808e"));
        String installerLocation = dao.getAndSetInstallerLocation(fpuid);
        if (installerLocation == null) {
            // XXX: as of this writing, this should never happen, because we
            // are only ever setting fallbackProxyUserId for users as whom we
            // have fallback proxies running.  But this hints at how we can
            // allow users to run new fallback proxies: we just set their
            // fallbackProxyUserId to themselves and a proxy will be launched
            // next time they invite someone.

            // Ask cloudmaster to create an instance for this user.
            log.info("Ordering launch of new invited server for "
                     + fpuid);
            Map<String, Object> map = new HashMap<String, Object>();
            /* These aren't in LanternConstants because they are not handled
             * by the client, but by a Python bot.
             * (salt/cloudmaster/cloudmaster.py)
             */
            map.put("launch-invsrv-as", fpuid);
            map.put("launch-refrtok", refreshToken);
            new SQSUtil().send(map);
        } else if (installerLocation.equals(PENDING)) {
            log.info("Proxy is still starting up -- not sending invite");
        } else {
            sendInviteEmail(inviterName, inviterEmail, invitedEmail, installerLocation);
        }
    }

    public static void onInvitedServerUp(final String fallbackProxyUserId,
                                         final String installerLocation) {
        final Dao dao = new Dao();
        final Collection<Invite> invites =
            dao.setInstallerLocationAndGetAuthorizedInvites(
                    fallbackProxyUserId, installerLocation);
        // We will probably have several invites by the same user.  For each
        // inviter, we need their name, which is unlikely to change.  So let's
        // memoize these.
        Map<String, String> nameCache = new HashMap<String, String>();
        for (Invite invite : invites) {
            String inviterEmail = invite.getInviter();
            String inviterName = nameCache.get(inviterEmail);
            if (inviterName == null) {
                inviterName = dao.getUser(inviterEmail).getName();
                if (inviterName == null) {
                    // Mandrill does this too; we're only doing it here to
                    // avoid looking the LanternUser up again, since AFAIK
                    // Java HashMaps won't let me tell a missing key from one
                    // assigned the value null.
                    inviterName = inviterEmail;
                }
                nameCache.put(inviterEmail, inviterName);
            }
            sendInviteEmail(inviterName,
                            inviterEmail,
                            invite.getInvitee(),
                            installerLocation);
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
            dao.setInviteStatus(inviterEmail,
                                invitedEmail,
                                Invite.Status.sent);
        } catch (final IOException e) {
            log.warning("Could not send e-mail!\n"+ThreadUtils.dumpStack(e));
        }
    }
}
