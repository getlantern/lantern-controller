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


public class FallbackProxyLauncher {

    private static final transient Logger log =
        Logger.getLogger(FallbackProxyLauncher.class.getName());

    public static void sendInvite(final String inviterName,
                                  final String inviterEmail,
                                  final String invitedEmail) {

        final Dao dao = new Dao();

        // An invite only gets to this point once it has been authorized, so
        // this is a good place to make sure we record that fact.
        dao.setInviteStatus(inviterEmail,
                            invitedEmail,
                            Invite.Status.authorized);

        String fpuid = dao.findUser(inviterEmail).getFallbackProxyUserId();
        if (fpuid == null) {
            log.warning("Old invite; we'll process this when the user gets"
                        + " a fallbackProxyUserId");
            return;
        }

        String instanceId = dao.findUser(fpuid).getFallbackForNewInvitees();
        if (instanceId == null) {
            log.info("Launching first proxy for " + fpuid);
            launchNewProxy(fpuid);
        } else if (instanceId.equals(
                    LanternControllerConstants.FALLBACK_PROXY_LAUNCHING)) {
            log.info("Proxy is still starting up; holding invite.");
        } else if (incrementFallbackInvites(fpuid, 1)) {
            log.info("Proxy is full; launching a new one.");
        } else {
            String installerLocation =
                  dao.findLanternInstance(fpuid, instanceId).getInstallerLocation();
            if (installerLocation == null) {
                throw new RuntimeException("Proxy without installerLocation?");
            }
            sendInviteEmail(inviterName,
                            inviterEmail,
                            invitedEmail,
                            installerLocation);
        }
    }

    public static void onFallbackProxyUp(final String fallbackProxyUserId,
                                         final String instanceId,
                                         final String installerLocation) {
        final Dao dao = new Dao();
        dao.setInstallerLocation(fallbackProxyUserId,
                                 instanceId,
                                 installerLocation);
        final Collection<Invite> invites =
            dao.setFallbackAndGetAuthorizedInvites(
                    fallbackProxyUserId, instanceId);
        incrementFallbackInvites(fallbackProxyUserId,
                                 invites.size());
        // We will probably have several invites by the same user.  For each
        // inviter, we need their name, which won't change.  So let's cache
        // these to avoid hitting the datastore multiple times for each inviter.
        Map<String, String> nameCache = new HashMap<String, String>();
        for (Invite invite : invites) {
            String inviterEmail = invite.getInviter();
            String inviterName = nameCache.get(inviterEmail);
            if (inviterName == null) {
                inviterName = dao.findUser(inviterEmail).getName();
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

    /** Increment number of invites for the currently filling fallback proxy
     * for this user, launching a new one if necessary.
     *
     * @return whether a new proxy has started launching since making this
     * call.
     */
    private static boolean incrementFallbackInvites(String userId,
                                                    int increment) {
        Dao dao = new Dao();
        Integer newInvites = dao.incrementFallbackInvites(userId,
                                                          increment);
        log.info("New invite count is " + newInvites);
        if (newInvites == null) {
            // Because of a race condition, someone managed to start a new proxy
            // while we were trying to increment the invites for the old one.
            return true;
        // We start a new proxy as soon as we hit the maximum, without waiting
        // to exceed it, because this simplifies the logic slightly and these
        // numbers are gross approximations anyway.
        } else if (newInvites >= dao.getMaxInvitesPerProxy()) {
            launchNewProxy(userId);
            return true;
        } else {
            return false;
        }
    }

    private static void launchNewProxy(String userId) {
        Dao dao = new Dao();
        String refreshToken = dao.findUser(userId).getRefreshToken();
        if (refreshToken == null) {
            throw new RuntimeException(
                    "Fallback proxy user without refresh token?" + userId);
        }
        String launching = LanternControllerConstants.FALLBACK_PROXY_LAUNCHING;
        if (launching.equals(dao.setUserFallback(userId, launching))) {
            log.warning("We were already launching a proxy for this user.");
            return;
        }
        log.info("Ordering launch of new fallback proxy for " + userId);
        Map<String, Object> map = new HashMap<String, Object>();
        /* These aren't in LanternConstants because they are not handled
         * by the client, but by a Python bot.
         * (salt/cloudmaster/cloudmaster.py)
         */
        map.put("launch-fp-as", userId);
        map.put("launch-refrtok", refreshToken);
        new SQSUtil().send(map);
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
        LanternUser user = dao.findUser(invitedEmail);

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
