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

    private final String[] liveFallbacks
        = {"fp-from-old-controller-96-at-getlantern-dot-org-8f94-1-2014-4-8",
            "fp-from-old-controller-25-at-getlantern-dot-org-1616-1-2014-4-8",
            "fp-from-old-controller-23-at-getlantern-dot-org-f1f8-1-2014-4-9",
            "fp-from-old-controller-41-at-getlantern-dot-org-6708-1-2014-4-8",
            "fp-from-old-controller-19-at-getlantern-dot-org-f937-1-2014-4-9",
            "fp-from-old-controller-3-at-getlantern-dot-org-08a5-1-2014-4-8",
            "fp-from-old-controller-78-at-getlantern-dot-org-a00c-1-2014-4-9",
            "fp-from-old-controller-87-at-getlantern-dot-org-795e-1-2014-4-9",
            "fp-from-old-controller-51-at-getlantern-dot-org-b1d5-1-2014-4-9",
            "fp-from-old-controller-43-at-getlantern-dot-org-4b76-1-2014-4-8",
            "fp-from-old-controller-5-at-getlantern-dot-org-f745-1-2014-4-8",
            "fp-from-old-controller-81-at-getlantern-dot-org-58ec-1-2014-4-9",
            "fp-from-old-controller-82-at-getlantern-dot-org-5853-1-2014-4-8",
            "fp-invite-at-getlantern-dot-org-7600-1-2014-2-17",
            "fp-from-old-controller-4-at-getlantern-dot-org-4b4e-1-2014-4-8",
            "fp-from-old-controller-39-at-getlantern-dot-org-356f-1-2014-4-9",
            "fp-from-old-controller-97-at-getlantern-dot-org-c643-1-2014-4-9",
            "fp-from-old-controller-77-at-getlantern-dot-org-ddbf-1-2014-4-9",
            "fp-from-old-controller-50-at-getlantern-dot-org-8fe6-1-2014-4-8",
            "fp-from-old-controller-29-at-getlantern-dot-org-6fce-1-2014-4-8",
            "fp-from-old-controller-10-at-getlantern-dot-org-3942-1-2014-4-8",
            "fp-from-old-controller-37-at-getlantern-dot-org-58fb-1-2014-4-8",
            "fp-from-old-controller-59-at-getlantern-dot-org-5db3-1-2014-4-9",
            "fp-from-old-controller-74-at-getlantern-dot-org-ee40-1-2014-4-8",
            "fp-from-old-controller-16-at-getlantern-dot-org-900c-1-2014-4-8",
            "fp-from-old-controller-70-at-getlantern-dot-org-2e74-1-2014-4-8",
            "fp-from-old-controller-93-at-getlantern-dot-org-acc7-1-2014-4-8",
            "fp-from-old-controller-99-at-getlantern-dot-org-ff9f-1-2014-4-9",
            "fp-from-old-controller-38-at-getlantern-dot-org-6f70-1-2014-4-8",
            "fp-from-old-controller-8-at-getlantern-dot-org-2b8e-1-2014-4-8",
            "fp-from-old-controller-76-at-getlantern-dot-org-7ac2-1-2014-4-8",
            "fp-afisk-at-getlantern-dot-org-50e8-4-2014-2-24",
            "fp-from-old-controller-91-at-getlantern-dot-org-9d17-1-2014-4-8",
            "fp-from-old-controller-15-at-getlantern-dot-org-e0b3-1-2014-4-8",
            "fp-from-old-controller-7-at-getlantern-dot-org-c277-1-2014-4-8",
            "fp-from-old-controller-2-at-getlantern-dot-org-0658-1-2014-4-8",
            "fp-from-old-controller-9-at-getlantern-dot-org-33ef-1-2014-4-8",
            "fp-from-old-controller-45-at-getlantern-dot-org-f5b4-1-2014-4-9",
            "fp-from-old-controller-13-at-getlantern-dot-org-3d3f-1-2014-4-8",
            "fp-from-old-controller-90-at-getlantern-dot-org-ad16-1-2014-4-9",
            "fp-from-old-controller-21-at-getlantern-dot-org-957a-1-2014-4-8",
            "fp-from-old-controller-95-at-getlantern-dot-org-960b-1-2014-4-9",
            "fp-from-old-controller-53-at-getlantern-dot-org-9add-1-2014-4-8",
            "fp-from-old-controller-49-at-getlantern-dot-org-2c00-1-2014-4-9",
            "fp-from-old-controller-17-at-getlantern-dot-org-1d45-1-2014-4-8",
            "fp-from-old-controller-55-at-getlantern-dot-org-89c9-1-2014-4-9",
            "fp-from-old-controller-62-at-getlantern-dot-org-117b-1-2014-4-8",
            "fp-from-old-controller-98-at-getlantern-dot-org-8362-1-2014-4-9",
            "fp-from-old-controller-75-at-getlantern-dot-org-8141-1-2014-4-9",
            "fp-from-old-controller-56-at-getlantern-dot-org-73c8-1-2014-4-9",
            "fp-from-old-controller-6-at-getlantern-dot-org-07a4-1-2014-4-8",
            "fp-from-old-controller-84-at-getlantern-dot-org-541f-1-2014-4-8",
            "fp-from-old-controller-52-at-getlantern-dot-org-5e8c-1-2014-4-9",
            "fp-from-old-controller-35-at-getlantern-dot-org-2313-1-2014-4-9",
            "fp-from-old-controller-30-at-getlantern-dot-org-6348-1-2014-4-8",
            "fp-from-old-controller-27-at-getlantern-dot-org-8e1c-1-2014-4-8",
            "fp-from-old-controller-63-at-getlantern-dot-org-5734-1-2014-4-8",
            "fp-from-old-controller-22-at-getlantern-dot-org-1657-1-2014-4-9",
            "fp-from-old-controller-85-at-getlantern-dot-org-7360-1-2014-4-8",
            "fp-from-old-controller-89-at-getlantern-dot-org-c12c-1-2014-4-9",
            "fp-from-old-controller-31-at-getlantern-dot-org-8d17-1-2014-4-8",
            "fp-from-old-controller-71-at-getlantern-dot-org-dbc5-1-2014-4-8",
            "fp-from-old-controller-46-at-getlantern-dot-org-2feb-1-2014-4-8",
            "fp-from-old-controller-66-at-getlantern-dot-org-6d01-1-2014-4-8",
            "fp-from-old-controller-65-at-getlantern-dot-org-4502-1-2014-4-8",
            "fp-from-old-controller-32-at-getlantern-dot-org-3d3a-1-2014-4-8",
            "fp-from-old-controller-40-at-getlantern-dot-org-5067-1-2014-4-8",
            "fp-from-old-controller-11-at-getlantern-dot-org-1f3f-1-2014-4-8",
            "fp-from-old-controller-28-at-getlantern-dot-org-4d0f-1-2014-4-8",
            "fp-from-old-controller-57-at-getlantern-dot-org-bb27-1-2014-4-8",
            "fp-from-old-controller-80-at-getlantern-dot-org-d31b-1-2014-4-9",
            "fp-from-old-controller-12-at-getlantern-dot-org-acc0-1-2014-4-8",
            "fp-from-old-controller-34-at-getlantern-dot-org-bca4-1-2014-4-8",
            "fp-from-old-controller-1-at-getlantern-dot-org-4c59-1-2014-4-8",
            "fp-from-old-controller-60-at-getlantern-dot-org-2f0d-1-2014-4-8",
            "fp-from-old-controller-58-at-getlantern-dot-org-3c02-1-2014-4-8",
            "fp-from-old-controller-18-at-getlantern-dot-org-ee4a-1-2014-4-8",
            "fp-from-old-controller-94-at-getlantern-dot-org-7c06-1-2014-4-9",
            "fp-from-old-controller-83-at-getlantern-dot-org-495e-1-2014-4-8",
            "fp-from-old-controller-88-at-getlantern-dot-org-0483-1-2014-4-9",
            "fp-from-old-controller-67-at-getlantern-dot-org-1580-1-2014-4-8",
            "fp-from-old-controller-14-at-getlantern-dot-org-bf02-1-2014-4-8",
            "fp-from-old-controller-42-at-getlantern-dot-org-a7f7-1-2014-4-8",
            "fp-from-old-controller-48-at-getlantern-dot-org-2b81-1-2014-4-9",
            "fp-from-old-controller-64-at-getlantern-dot-org-097f-1-2014-4-9",
            "fp-from-old-controller-72-at-getlantern-dot-org-7d82-1-2014-4-8",
            "fp-from-old-controller-54-at-getlantern-dot-org-8eb6-1-2014-4-9",
            "fp-from-old-controller-47-at-getlantern-dot-org-8826-1-2014-4-9",
            "fp-from-old-controller-20-at-getlantern-dot-org-7379-1-2014-4-8",
            "fp-from-old-controller-68-at-getlantern-dot-org-8e95-1-2014-4-9",
            "fp-from-old-controller-73-at-getlantern-dot-org-9f33-1-2014-4-9",
            "fp-from-old-controller-61-at-getlantern-dot-org-ffbe-1-2014-4-8",
            "fp-from-old-controller-33-at-getlantern-dot-org-fbc7-1-2014-4-9",
            "fp-from-old-controller-69-at-getlantern-dot-org-46a6-1-2014-4-8",
            "fp-from-old-controller-36-at-getlantern-dot-org-452a-1-2014-4-9",
            "fp-from-old-controller-86-at-getlantern-dot-org-3ea1-1-2014-4-9",
            "fp-from-old-controller-24-at-getlantern-dot-org-76bb-1-2014-4-9",
            "fp-from-old-controller-79-at-getlantern-dot-org-dc5d-1-2014-4-9",
            "fp-from-old-controller-0-at-getlantern-dot-org-d136-1-2014-4-8",
            "fp-from-old-controller-92-at-getlantern-dot-org-b568-1-2014-4-8",
            "fp-from-old-controller-26-at-getlantern-dot-org-5553-1-2014-4-8",
            "fp-from-old-controller-44-at-getlantern-dot-org-9363-1-2014-4-8"}

    @Override
    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response) {
        String input = request.getParameter("input");
        log.info("Running maintenance task with input " + input);
        try {
            // This space reserved for your hacks.  Deploy them, run them,
            // delete/disable them, redeploy with them deleted/disabled.  DON'T
            // LEAVE THEM ENABLED, EITHER IN GITHUB OR GAE!
            if (input == "mark") {
                markBadFallbacks();
            } else if (input.startsWith("upload")) {
                uploadFallbacks();
            }
        } catch (Exception e) {
            // In no case we want to keep retrying this.
            log.severe("" + e);
        }
        LanternControllerUtils.populateOKResponse(response, "OK");
    }


    private void markBadFallbacks() {
        Dao dao = new Dao();
        Objectify ofy = dao.ofy();
        List<LanternInstance> allFallbacks = ofy.query(LanternInstance.class)
                                                .filter("isFallbackProxy", true)
                                                .filter("fallbackProxyShutdown", false)
                                                .list();
        Set<String> live = new HashSet<String>(Arrays.asList(liveFallbacks));
        for (LanternInstance fallback : allFallbacks) {
            if (!live.contains(fallback.getId()) {
                fallback.setFallbackProxyShutdown(true);
                ofy.put(fallback);
            }
        }
    }

    private void uploadFallbacks() {
        S3Config.uploadConfig(
            LanternControllerConstants.FALLBACK_CHECKER_CONFIG_FOLDER,
            compileFallbackCheckerConfig());
    }

    public static String compileFallbackCheckerConfig() {
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

    private void uploadFallbacks() {
        Dao dao = new Dao();
        Objectify ofy = dao.ofy();
        List<LanternInstance> allFallbacks = ofy.query(LanternInstance.class)
                                                .filter("isFallbackProxy", true)
                                                .filter("fallbackProxyShutdown", false)
                                                .list();
        StringBuilder
        for (LanternInstance fallback : allFallbacks) {

        }
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
