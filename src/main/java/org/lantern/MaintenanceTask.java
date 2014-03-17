package org.lantern;

import java.net.URL;
import java.util.logging.Logger;
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
import org.lantern.data.LanternFriend;
import org.lantern.data.LanternUser;
import org.lantern.loggly.LoggerFactory;

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;


@SuppressWarnings("serial")
public class MaintenanceTask extends HttpServlet {

    private static final transient Logger log = LoggerFactory
            .getLogger(MaintenanceTask.class);

    @Override
    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response) {
        String input = request.getParameter("input");
        log.info("Running maintenance task with input " + input);
        try {
            // This space reserved for your hacks.  Deploy them, run them,
            // delete/disable them, redeploy with them deleted/disabled.  DON'T
            // LEAVE THEM ENABLED, EITHER IN GITHUB OR GAE!
            log.info("Maintenance tasks are disabled currently.");
        } catch (Exception e) {
            // In no case we want to keep retrying this.
            log.severe("" + e);
        }
        LanternControllerUtils.populateOKResponse(response, "OK");
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
                        "fallback-id");
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

    private void refreshAllWrappers() {
        Dao dao = new Dao();
        List<LanternUser> users = dao.ofy().query(LanternUser.class).list();
        for (LanternUser user : users) {
            try {
                if (user.getConfigFolder() != null) {
                    S3Config.enqueueWrapperUploadRequest(user.getId(),
                                                         user.getConfigFolder());
                }
            } catch (Exception e) {
                // This will happen for the root user.
                log.warning("Exception trying to refresh config: " + e);
            }
        }
    }

    private void checkConfigFolders(String input) {

        Dao dao = new Dao();
        int nulls = 0;
        int ok = 0;
        int fishy = 0;

        int expectedNumberOfFallbacks = 2;

        HashSet<String> tokens = new HashSet<String>();
        HashSet<String> certs = new HashSet<String>();

        List<LanternUser> users = dao.ofy().query(LanternUser.class).list();
        for (LanternUser user : users) {
            log.info("Checking " + user.getId());
            String configFolder = user.getConfigFolder();
            if (configFolder == null) {
                log.info("Null config!");
                nulls += 1;
                continue;
            }
            boolean goodToken;
            boolean goodCert;
            try {
                URL url = new URL("https://s3-ap-southeast-1.amazonaws.com/lantern-config/"
                        + configFolder
                        + "/config.json");
                Map<String, Object> m = new ObjectMapper().readValue(
                        url.openStream(), Map.class);
                List<Map<String, Object>> fbs = (List)m.get("fallbacks");
                if (fbs.size() != 1) {
                    throw new RuntimeException("Weird-sized fallback list: " + fbs.size());
                }
                String authToken = (String)fbs.get(0).get("auth_token");
                String cert = (String)fbs.get(0).get("cert");

                if (authToken == null) {
                    log.info("Null token!");
                    goodToken = false;
                } else if (tokens.contains(authToken)) {
                    log.info("Good token.");
                    goodToken = true;
                } else if (tokens.size() < expectedNumberOfFallbacks) {
                    log.info("Saw token " + authToken + " for the first time.  Adding.");
                    tokens.add(authToken);
                    goodToken = true;
                } else {
                    log.info("BAD TOKEN: " + authToken);
                    goodToken = false;
                }
                if (cert == null) {
                    log.info("Null cert!");
                    goodCert = false;
                } else if (certs.contains(cert)) {
                    log.info("Good cert.");
                    goodCert = true;
                } else if (certs.size() < expectedNumberOfFallbacks) {
                    log.info("Saw cert " + cert + " for the first time.  Adding.");
                    certs.add(cert);
                    goodCert = true;
                } else {
                    log.info("BAD cert: " + cert);
                    goodCert = false;
                }
                if (goodToken && goodCert) {
                    ok += 1;
                } else {
                    fishy += 1;
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        log.info("OK: " + ok + "; fishy: " + fishy + "; null: " + nulls
                 + "; certs: " + certs.size() + "; tokens:" + tokens.size());
    }
}
