package org.lantern;

import java.net.URL;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.apphosting.api.DeadlineExceededException;

import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.lantern.data.Dao;
import org.lantern.data.Dao.DbCall;
import org.lantern.data.FriendingQuota;
import org.lantern.data.Invite;
import org.lantern.data.LanternInstance;
import org.lantern.data.LanternFriend;
import org.lantern.data.LanternUser;
import org.lantern.loggly.LoggerFactory;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;


@SuppressWarnings("serial")
public class MaintenanceTask extends HttpServlet {

    private static final transient Logger log = LoggerFactory
            .getLogger(MaintenanceTask.class);

    private final String[] liveFallbacks = {};

    @Override
    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response) {
        String input = request.getParameter("input");
        log.info("Running maintenance task with input " + input);
        try {
            // This space reserved for your hacks.  Deploy them, run them,
            // delete/disable them, redeploy with them deleted/disabled.  DON'T
            // LEAVE THEM ENABLED, EITHER IN GITHUB OR GAE!
            //log.info("Maintenance tasks are disabled.");
            log.info("Sending stuck invite emails");
            sendStuckInviteEmails();
        } catch (Exception e) {
            // In no case we want to keep retrying this.
            log.severe("" + e);
        }
        LanternControllerUtils.populateOKResponse(response, "OK");
    }

    private void sendStuckInviteEmails() {
        Dao dao = new Dao();
        Objectify ofy = dao.ofy();
        HashSet<String> processed = new HashSet<String>();
        ArrayList<String> toSend = new ArrayList<String>();
        for (Invite invite : ofy.query(Invite.class)
                                .filter("status", Invite.Status.authorized)
                                .limit(1000)
                                .list()) {
            String inviteeId = invite.getInvitee();
            if (processed.contains(inviteeId)) {
                continue;
            }
            processed.add(inviteeId);
            LanternUser invitee = dao.findUser(inviteeId);
            if (!invitee.isEverSignedIn()) {
                toSend.add(inviteeId);
            }
        }
        for (String inviteeId : toSend) {
            dao.sendInvitesTo(inviteeId);
        }
    }


    private void markBadFallbacks() {
        Dao dao = new Dao();
        Objectify ofy = dao.ofy();
        Set<String> live = new HashSet<String>(Arrays.asList(liveFallbacks));
        List<LanternInstance> allFallbacks = ofy.query(LanternInstance.class)
                                                .filter("isFallbackProxy", true)
                                                .list();
        log.info("Got " + allFallbacks.size() + " fallbacks.");
        for (LanternInstance fallback : allFallbacks) {
            if (live.contains(fallback.getId())) {
                log.info(fallback.getId() + " is one of the live ones.");
            } else {
                log.info("Marking " + fallback.getId() + " as dead.");
                fallback.setFallbackProxyShutdown(true);
                ofy.put(fallback);
            }
        }
    }

    public static String compileFallbackCheckerConfig() {
    	Dao dao = new Dao();
        List<LanternInstance> allFallbacks = dao.ofy().query(LanternInstance.class)
                .filter("isFallbackProxy", true)
                .filter("fallbackProxyShutdown", false)
                .list();
        StringBuilder sb = new StringBuilder("[");
        sb.append(allFallbacks.get(0).getAccessData());
        for (int i=1; i<allFallbacks.size(); ++i) {
            sb.append(",");
            sb.append(allFallbacks.get(i).getAccessData());
        }
        sb.append("]");
        return sb.toString();
    }

    private void launchOneHundredFallbacks() {
        Objectify ofy = new Dao().ofy();
        for (int i=0; i<100; i++) {
            String fpuid = "from-old-controller-" + i + "@getlantern.org";
            LanternUser u = new LanternUser(fpuid);
            u.setFallbackProxyUserId(fpuid);
            ofy.put(u);
            FallbackProxyLauncher.launchNewProxy(fpuid);
        }
    }

    private void normalizeFriendEmails() {
        Dao dao = new Dao();
        for (LanternFriend friend : dao.ofy().query(LanternFriend.class).list()) {
            String userId = EmailAddressUtils.normalizedEmail(friend.getUserEmail());
            String id = EmailAddressUtils.normalizedEmail(friend.getEmail());
            if (!friend.getEmail().equals(id)
                || !friend.getUserEmail().equals(userId)) {
                log.info("Normalizing " + friend.getUserEmail() + "'s friend " + friend.getEmail());
                normalizeFriendEmails(friend.getUserEmail(), friend.getId());
                Invite invite = dao.getInvite(dao.ofy(), userId, id);
                if (invite != null
                    && invite.getStatus() == Invite.Status.authorized) {
                    log.info("Processing invite...");
                    FallbackProxyLauncher.processAuthorizedInvite(userId, id);
                }
            }
        }
    }

    private void normalizeFriendEmails(final String userId, final long friendId) {
        new Dao().withTransaction(new DbCall<Void>() {
            @Override
            public Void call(Objectify ofy) {
                LanternFriend friend = ofy.find(Key.create(Key.create(FriendingQuota.class, userId),
                                                           LanternFriend.class, friendId));
                if (friend == null) {
                    log.severe("Found no friend with id " + friendId);
                    return null;
                }
                ofy.delete(friend);
                friend.setQuota(Key.create(FriendingQuota.class, EmailAddressUtils.normalizedEmail(userId)));
                friend.normalizeEmails();
                ofy.put(friend);
                return null;
            }
        });
    }

    private void moveToNewFallback(String oldFpuId, String oldFallbackId) {
        Dao dao = new Dao();
        Set<String> users = new HashSet<String>();
        for (LanternUser user : dao.ofy().query(LanternUser.class)
                                   .filter("fallbackProxy",
                                           dao.getInstanceKey(oldFpuId,
                                                              oldFallbackId))
                                   .list()) {
            users.add(user.getId());
        }
        moveToNewFallback(users);
    }

    private void moveToNewFallback() {
        Dao dao = new Dao();
        Objectify ofy = dao.ofy();
        Set<String> toMove = new HashSet<String>();
        // So we add this one to the trusted fallback, but not its invitees...
        toMove.add("overinviter@gmail.com");
        Set<String> frontier = new HashSet<String>();
        // Here we would add the root sponsor, plus any invitees of the
        // overinviter that we want to preserve in the trusted fallback.
        frontier.add("example@getlantern.org");
        while (!frontier.isEmpty()) {
            String next = frontier.iterator().next();
            toMove.add(next);
            frontier.remove(next);
            for (LanternUser user : ofy.query(LanternUser.class).filter("sponsor", next).list()) {
                String userId = user.getId();
                if (!toMove.contains(userId)) {
                    frontier.add(userId);
                }
            }
        }
        moveToNewFallback(toMove);
    }

    public void moveToNewFallback(Set<String> toMove) {
        Dao dao = new Dao();
        while (!toMove.isEmpty()) {
            String userId = toMove.iterator().next();
            try {
                dao.moveToNewFallback(
                        userId,
                        "some-fallback-proxy-user-id@getlantern.org",
                        "instance-id-of-the-fallback");
            } catch (DeadlineExceededException e) {
                log.warning("Got DEE; requeuing...");
                QueueFactory.getDefaultQueue().add(
                        TaskOptions.Builder
                        .withUrl("/maintenance_task")
                        .param("input", StringUtils.join(toMove, ' ')));
                return;
            } catch (Exception e) {
                log.severe("Error trying to move " + userId
                           + " to new fallback: " + e);
            }
            toMove.remove(userId);
        }
    }
}
