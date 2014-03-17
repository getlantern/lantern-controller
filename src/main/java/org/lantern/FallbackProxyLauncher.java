package org.lantern;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.lantern.data.Dao;
import org.lantern.data.Dao.DbCall;
import org.lantern.data.FallbackProxy;
import org.lantern.data.Invite;
import org.lantern.data.LanternUser;
import org.lantern.loggly.LoggerFactory;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;

import com.googlecode.objectify.Objectify;


/**
 * Process authorized invites, split fallback proxies as required. */
public class FallbackProxyLauncher {

    //XXX: "launcher" is a misnomer now that launching itself is performed
    // in a task queue elsewhere.  A more thorough refactoring is probably
    // needed anyway, so I won't mess with this just yet. - aranhoide

    private static final transient Logger log =
        LoggerFactory.getLogger(FallbackProxyLauncher.class);

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
    public static boolean authorizeInvite(String inviterEmail,
                                          String inviteeEmail) {
        inviterEmail = EmailAddressUtils.normalizedEmail(inviterEmail);
        inviteeEmail = EmailAddressUtils.normalizedEmail(inviteeEmail);
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
        processAuthorizedInvite(inviterEmail, inviteeEmail);
        return true;
    }

    public static void processAuthorizedInvite(String inviterEmail,
                                               String inviteeEmail) {
        inviterEmail = EmailAddressUtils.normalizedEmail(inviterEmail);
        inviteeEmail = EmailAddressUtils.normalizedEmail(inviteeEmail);
        final Dao dao = new Dao();
        dao.findUser(inviterEmail).getFallbackProxy().getName();
        dao.createInvitee(inviteeEmail,
                          inviterEmail,
                          dao.findUser(inviterEmail)
                             .getFallbackProxy()
                             .getName());
    }

    public static void onFallbackProxyUp(final String fallbackId,
                                         final String accessData,
                                         final String ip) {
        Dao dao = new Dao();
        String parentId = dao.withTransaction(new DbCall<String>() {
            @Override
            public String call(Objectify ofy) {
                FallbackProxy fp = ofy.find(FallbackProxy.class, fallbackId);
                fp.setAccessData(accessData);
                fp.setIp(ip);
                fp.setStatus(FallbackProxy.Status.active);
                ofy.put(fp);
                return fp.getParent();
            }
        });
        splitFallbackIfReady(parentId);
    }

    private static void splitFallbackIfReady(final String fallbackId) {
        Dao dao = new Dao();
        int numFallbacksUp = dao.ofy().query(FallbackProxy.class)
                                      .filter("parent", fallbackId)
                                      .filter("status", FallbackProxy.Status.active)
                                      .count();
        if (numFallbacksUp < 2) {
            return;
        }
        // Transaction to ensure we only ever enqueue this once for this
        // fallback.
        dao.withTransaction(new DbCall<Void>() {
            @Override
            public Void call(Objectify ofy) {
                FallbackProxy fp = ofy.find(FallbackProxy.class, fallbackId);
                if (fp.getStatus() == FallbackProxy.Status.active) {
                    fp.setStatus(FallbackProxy.Status.splitting);
                    ofy.put(fp);
                    QueueFactory.getDefaultQueue().add(
                        TaskOptions.Builder
                           .withUrl(SplitFallbackTask.PATH)
                           .param("fallback_id", fallbackId));
                } else {
                    log.warning("Fallback in unexpected state: " + fp.getStatus());
                }
                return null;
            }
        });
    }

}
