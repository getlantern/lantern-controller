package org.lantern;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.logging.Logger;
import java.util.HashMap;
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

import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;

import org.lantern.loggly.LoggerFactory;
import org.lantern.data.Dao;
import org.lantern.data.Dao.DbCall;
import org.lantern.data.FallbackProxy;
import org.lantern.data.LanternUser;


@SuppressWarnings("serial")
public class SplitFallbackTask extends ExtendedJob {

    private static final transient Logger log = LoggerFactory
            .getLogger(SplitFallbackTask.class);

    /**
     * Minimum ratio of number of users in smaller vs larger part of the
     * split.
     */
    private static final double TARGET_BALANCE = 0.7;

    public static final String PATH = "/split_fallback_task";

    private Set<String> allRoots;

    private class UserNode {
        public String id;
        public String sponsorId;
        public Set<UserNode> children = new HashSet<UserNode>();
        public int weight = 1;
    }

    @Override
    protected void start(HttpServletRequest request,
                         List<String> args) {
        String fallbackId = request.getParameter("fallbackId");
        log.info("Splitting fallback " + fallbackId);
        Dao dao = new Dao();
        Map<String, UserNode> usersById
            = new HashMap<String, UserNode>();
        for (LanternUser user
             : dao.ofy().query(LanternUser.class)
                        .filter("fallbackProxy", fallbackId)) {
            UserNode node = new UserNode();
            node.id = user.getId();
            node.sponsorId = user.getSponsor();
            usersById.put(node.id, node);
        }
        Set<UserNode> maxRoots = new HashSet<UserNode>();
        for (UserNode user : usersById.values()) {
            if (usersById.containsKey(user.sponsorId)) {
                usersById.get(user.sponsorId).children.add(user);
            } else {
                user.sponsorId = null;
                maxRoots.add(user);
            }
        }
        for (UserNode root : maxRoots) {
            initializeWeights(root);
        }
        Set<UserNode> minRoots = new HashSet<UserNode>();
        double oldBalance = 0.0;
        for (;;) {
            int minSum = sumWeights(minRoots);
            int maxSum = sumWeights(maxRoots);
            if (minSum > maxSum) {
                Set<UserNode> tmp = minRoots;
                minRoots = maxRoots;
                maxRoots = tmp;
                continue;
            }
            double balance = getBalance(minSum, maxSum);
            if (balance >= TARGET_BALANCE) {
                allRoots = new HashSet<String>(minRoots.size() + maxRoots.size());
                for (UserNode user : minRoots) {
                    allRoots.add(user.id);
                }
                for (UserNode user : maxRoots) {
                    allRoots.add(user.id);
                }
                List<FallbackProxy> successors
                    = dao.ofy().query(FallbackProxy.class)
                               .filter("parent", fallbackId)
                               .list();
                feedUsers(minRoots, successors.get(0).getId(), args);
                feedUsers(maxRoots, successors.get(1).getId(), args);
                doContinue(null, args);
                return;
            }
            if (balance < oldBalance) {
                UserNode toSplit = heaviestUser(maxRoots);
                UserNode toExtract = bestToChange(toSplit.children,
                                                  minSum,
                                                  maxSum);
                toSplit.children.remove(toExtract);
                toSplit.weight -= toExtract.weight;
                minRoots.add(toExtract);
                oldBalance = 0.0;
            } else {
                UserNode bestUser = bestToChange(maxRoots, minSum, maxSum);
                maxRoots.remove(bestUser);
                minRoots.add(bestUser);
                oldBalance = balance;
            }
        }
    }

    @Override
    protected void processOneArg(String arg, ArgPusher argPusher) {
        Dao dao = new Dao();
        String[] parts = arg.split(" ");
        String userId = parts[0];
        String fallbackId = parts[1];
        dao.moveToNewFallback(userId, fallbackId);
        for (Key<LanternUser> childKey : dao.ofy().query(LanternUser.class)
                                                  .filter("sponsor", userId)
                                                  .fetchKeys()) {
            String childId = childKey.getName();
            if (!allRoots.contains(childId)) {
                argPusher.pushArg(childId + " " + fallbackId);
            }
        }

    }

    @Override
    protected void doContinue(HttpServletRequest request,
                              List<String> args) {
        if (request != null) {
            allRoots = new HashSet<String>(
                    Arrays.asList(
                        request.getParameter("allRoots").trim().split(" ")));
        }
        super.doContinue(request, args);
    }

    private void feedUsers(Set<UserNode> users,
                           String fallbackId,
                           List<String> args) {
        for (UserNode user : users) {
            args.add(user.id + " " + fallbackId);
        }
    }

    private void initializeWeights(UserNode user) {
        for (UserNode child : user.children) {
            initializeWeights(child);
            user.weight += child.weight;
        }
    }

    private double getBalance(int a, int b) {
        return (double)Math.min(a, b) / (double)Math.max(a, b);
    }

    private UserNode bestToChange(Collection<UserNode> users, int minSum, int maxSum) {
        UserNode bestUser = null;
        double bestBalance = 0.0;
        for (UserNode user : users) {
            double balance = getBalance(minSum + user.weight,
                                        maxSum - user.weight);
            if (balance >= bestBalance) {
                bestUser = user;
                bestBalance = balance;
            }
        }
        return bestUser;
    }

    private UserNode heaviestUser(Collection<UserNode> users) {
        UserNode heaviest = null;
        int maxWeight = 0;
        for (UserNode user : users) {
            if (user.weight > maxWeight) {
                maxWeight = user.weight;
                heaviest = user;
            }
        }
        return heaviest;
    }

    private int sumWeights(Collection<UserNode> users) {
        int sum = 0;
        for (UserNode user : users) {
            sum += user.weight;
        }
        return sum;
    }

    @Override
    protected TaskOptions customizeTaskOptions(TaskOptions to) {
        return to.param("allRoots", StringUtils.join(allRoots, ' '));
    }
}
