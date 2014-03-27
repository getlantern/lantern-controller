package org.lantern;

import java.text.SimpleDateFormat;
import java.util.Calendar;
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
        dao.createInvitee(inviteeEmail,
                          inviterEmail);
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
        if (parentId == null) {
            log.info("No parent to split.");
        } else {
            splitFallbackIfReady(parentId);
        }
    }

    private static void splitFallbackIfReady(final String fallbackId) {
        Dao dao = new Dao();
        int numFallbacksUp = dao.ofy().query(FallbackProxy.class)
                                      .filter("parent", fallbackId)
                                      .filter("status", FallbackProxy.Status.active)
                                      .count();
        if (numFallbacksUp < 2) {
            log.info("Only " + numFallbacksUp + " fallbacks so far.  Not ready to split parent yet.");
            return;
        }
        log.info("Splitting fallback " + fallbackId);
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

    /**
     * Create a fallback proxy entry in the datastore.
     *
     * The serial number is a hint; a new one may be created in the unlikely
     * event that another proxy with the same one already exists.
     */
    public static String createProxy(final String parentId,
                                     final int serialNo) {
        Dao dao = new Dao();
        final String family;
        if (parentId == null) {
            family = null;
        } else {
            FallbackProxy parent
                = dao.ofy().find(FallbackProxy.class, parentId);
            family = parent == null ? null : parent.getFamily();
        }
        final String familyStr = family == null ? "" : family + "-";
        final String date = new SimpleDateFormat("yyyy-MM-dd").format(
                Calendar.getInstance().getTime());
        return dao.withTransaction(new DbCall<String>() {
            @Override
            public String call(Objectify ofy) {
                for (int serial = serialNo;
                     /* run until return */;
                     serial += 2 /* maintain parity */) {
                    // DRY: The "fp-" prefix is important.  The lantern_aws
                    // scripts assume that minion names match this.  I tried
                    // maintaining that invariant purely in the lantern_aws
                    // end, but that created a more annoying and error-prone
                    // distinction between fallback id and minion name
                    // ("fp-" + fallback_id).
                    String id = "fp-" + familyStr + date + "-" + serial;
                    FallbackProxy fp = ofy.find(FallbackProxy.class, id);
                    if (fp != null) {
                        continue;
                    }
                    log.info("Creating proxy: id=" + id
                             + "; parentId=" + parentId
                             + "; family=" + family);
                    ofy.put(new FallbackProxy(id, parentId, family));
                    return id;
                }
            }
        });
    }

    public static void requestProxyLaunch(String fallbackId) {
        Map<String, Object> map = new HashMap<String, Object>();
        /*
         * This isn't in LanternConstants because they are not handled
         * by the client, but by a Python bot.
         * (salt/cloudmaster/cloudmaster.py)
         */
        map.put("launch-id", fallbackId);
        new SQSUtil().send(map);
    }
}
