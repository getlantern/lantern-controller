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


/**
 * Process authorized invites, launch fallback proxies as required, and match
 * invitees to such proxies.
 */
public class FallbackProxyLauncher {

    private static final transient Logger log =
        Logger.getLogger(FallbackProxyLauncher.class.getName());

    public static void authorizeInvites(String[] ids) {
        for (String id : ids) {
            String[] parsed = Invite.parseId(id);
            authorizeInvite(parsed[0], parsed[1]);
        }
    }

    /**
     * Process invite authorization.
     *
     * Handle an invite once it has been authorized, or we have determined it
     * doesn't need authorization.
     */
    public static void authorizeInvite(final String inviterEmail,
                                       final String invitedEmail) {

        final Dao dao = new Dao();

        // TODO do this immediately but queue everything after this
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
            sendInviteEmail(inviterEmail,
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
        for (Invite invite : invites) {
            sendInviteEmail(invite.getInviter(),
                            invite.getInvitee(),
                            installerLocation);
        }
    }

    public static void demoteUserAndShutDownFallbacks(String userId) {
        Collection<String> instanceIds
            = new Dao().demoteUserAndMarkFallbacksShutDown(userId);
        for (String instanceId : instanceIds) {
            Map<String, Object> map = new HashMap<String, Object>();
            /* This is not in LanternConstants because it's not handled by the
             * client, but by a Python bot.  (salt/cloudmaster/cloudmaster.py)
             */
            map.put("shutdown-fp", instanceId);
            new SQSUtil().send(map);
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

    /**
     * Order a new fallback proxy to be launched.
     *
     * The proxy will run as the given user.
     */
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
        Integer serial = dao.incrementFallbackSerialNumber(userId);
        // Grammar be damned :).
        log.info("Ordering launch of " + serial + "th fallback proxy for "
                 + userId);
        Map<String, Object> map = new HashMap<String, Object>();
        /* These aren't in LanternConstants because they are not handled
         * by the client, but by a Python bot.
         * (salt/cloudmaster/cloudmaster.py)
         */
        map.put("launch-fp-as", userId);
        map.put("launch-refrtok", refreshToken);
        map.put("launch-serial", serial);
        new SQSUtil().send(map);
    }

    /**
     * Ask MandrillEmailer to send an invite e-mail.
     *
     * 'Unpack' the parameters into the format MandrillEmailer expects.
     */
    private static void sendInviteEmail(final String inviterEmail,
                                        final String invitedEmail,
                                        final String installerLocation) {
        final Dao dao = new Dao();
        LanternUser inviter = dao.prepareToSendInvite(inviterEmail, invitedEmail);
        if (inviter == null) {
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
            MandrillEmailer.sendInvite(inviter.getName(), inviterEmail, invitedEmail,
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
