package org.lantern;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;

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

    public static int authorizeInvites(String[] ids) {
        int totalAuthorized = 0;
        
        for (String id : ids) {
            String[] parsed = Invite.parseId(id);
            if (authorizeInvite(parsed[0], parsed[1])) {
                totalAuthorized += 1;
            }
        }
        
        return totalAuthorized;
    }
    
    /**
     * Process invite authorization.
     *
     * Handle an invite once it has been authorized, or we have determined it
     * doesn't need authorization.
     * 
     * @return true if invite was newly authorized
     */
    public static boolean authorizeInvite(final String inviterEmail,
                                       final String inviteeEmail) {
        log.info(String.format("Authorizing invite from %1$s to %2$s", inviterEmail, inviteeEmail));

        final Dao dao = new Dao();

        // TODO: for performance, we may want to do this immediately but queue
        // everything after this.
        boolean statusUpdated = dao.setInviteStatus(inviterEmail,
                                                    inviteeEmail,
                                                    Invite.Status.queued,
                                                    Invite.Status.authorized);
        
        if (!statusUpdated) {
            log.info("Declining to authorize invite that is not currently queued");
            return false;
        }
        return processAuthorizedInvite(inviterEmail, inviteeEmail);
    }

    public static boolean processAuthorizedInvite(String inviterEmail,
                                                  String inviteeEmail) {
        final Dao dao = new Dao();
        String fpuid = dao.findUser(inviterEmail).getFallbackProxyUserId();
        if (fpuid == null) {
            log.warning("Old invite; we'll process this when the user gets"
                        + " a fallbackProxyUserId");
            return true;
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
            dispatchInvite(inviterEmail,
                           inviteeEmail,
                           fpuid,
                           instanceId);
        }
        return true;
    }

    public static void onFallbackProxyUp(String fallbackProxyUserId,
                                         String instanceId,
                                         String installerLocation,
                                         String ip,
                                         String port) {
        final Dao dao = new Dao();
        dao.registerFallbackProxy(fallbackProxyUserId,
                                  instanceId,
                                  installerLocation,
                                  ip,
                                  port);
        final Collection<Invite> invites =
            dao.setFallbackAndGetAuthorizedInvites(
                    fallbackProxyUserId, instanceId);
        incrementFallbackInvites(fallbackProxyUserId,
                                 invites.size());
        for (Invite invite : invites) {
            dispatchInvite(invite.getInviter(),
                           invite.getInvitee(),
                           fallbackProxyUserId,
                           instanceId);
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
     * Perform updates necessary when an invite is good to go, and send the
     * invite email.
     *
     * Only a race condition could abort an invite at this point.
     */
    private static void dispatchInvite(String inviterEmail,
                                       String inviteeEmail,
                                       String fallbackProxyUserId,
                                       String instanceId) {
        final Dao dao = new Dao();
        if (!dao.setInviteStatus(inviterEmail,
                                 inviteeEmail,
                                 Invite.Status.authorized,
                                 Invite.Status.sending)) {
            log.severe("Bad invite state.");
            return;
        }
        LanternUser inviter = dao.findUser(inviterEmail);
        LanternUser invitee = dao.createInvitee(inviter,
                                                inviteeEmail,
                                                fallbackProxyUserId,
                                                instanceId);
        QueueFactory.getDefaultQueue().add(
            TaskOptions.Builder
               .withUrl("/send_invite_task")
               .param("inviterName", "" + inviter.getName()) // handle null
               .param("inviterEmail", inviterEmail)
               .param("inviteeEmail", inviteeEmail)
               .param("installerLocation",
                      dao.findInstance(fallbackProxyUserId, instanceId)
                         .getInstallerLocation())
               .param("inviteeEverSignedIn", "" + invitee.isEverSignedIn()));

    }
}
