package org.lantern.friending;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.lantern.EmailAddressUtils;
import org.lantern.LanternControllerConstants;
import org.lantern.data.Dao;
import org.lantern.data.Dao.DbCall;
import org.lantern.data.FriendingQuota;
import org.lantern.data.LanternFriend;
import org.lantern.data.LanternUser;
import org.lantern.loggly.LoggerFactory;
import org.lantern.messages.FriendResponse;
import org.lantern.state.Friend;
import org.lantern.state.Friend.Status;

import com.google.api.server.spi.response.UnauthorizedException;
import com.google.appengine.api.users.User;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.Objectify;

public class Friending {
    private static final Logger log = LoggerFactory.getLogger(Friending.class);

    public static FriendResponse<List<Friend>> listFriend(
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        log.info("Listing friends");
        final String userEmail = email(user);
        return new Dao()
                .withTransaction(new DbCall<FriendResponse<List<Friend>>>() {
                    @Override
                    public FriendResponse<List<Friend>> call(Objectify ofy) {
                        FriendingQuota quota = doGetOrCreateQuota(ofy,
                                userEmail);
                        if (quota == null) {
                            return failure();
                        }
                        List<Friend> friends = new ArrayList<Friend>(
                                ofy.query(LanternFriend.class)
                                        .ancestor(quota)
                                        .list());
                        Map<String, Friend> uniqueFriends = new HashMap<String, Friend>();
                        for (Friend friend : friends) {
                            uniqueFriends.put(friend.getEmail(), friend);
                        }
                        // Find friend relationships referencing this user and
                        // add them
                        // to the set of unique Friends as free to friend
                        List<LanternFriend> reverseFriends = new Dao()
                                .withObjectify(new DbCall<List<LanternFriend>>() {
                                    @Override
                                    public List<LanternFriend> call(
                                            Objectify ofy) {
                                        return ofy
                                                .query(LanternFriend.class)
                                                .filter("email", userEmail)
                                                .filter("status", Status.friend)
                                                .list();
                                    }
                                });
                        for (Friend friend : reverseFriends) {
                            Friend friendSuggestion = LanternFriend
                                    .reverseOf(friend);
                            Friend originalFriend = uniqueFriends
                                    .get(friendSuggestion.getEmail());
                            if (originalFriend == null
                                    || originalFriend.getStatus() == Status.pending) {
                                uniqueFriends.put(friendSuggestion.getEmail(),
                                        friendSuggestion);
                            }
                        }
                        List<Friend> allFriends = new ArrayList<Friend>(
                                uniqueFriends.values());
                        return success(quota, allFriends);
                    }
                });
    }

    public static FriendResponse<Friend> getFriend(final Long id,
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        final String userEmail = email(user);

        FriendResponse<Friend> resp = new Dao()
                .withTransaction(new DbCall<FriendResponse<Friend>>() {
                    @Override
                    public FriendResponse<Friend> call(Objectify ofy) {
                        FriendingQuota quota = doGetOrCreateQuota(ofy,
                                userEmail);
                        if (quota == null) {
                            return failure();
                        }
                        Friend existing = getExistingFriendById(ofy, quota, id);
                        return success(quota, existing);
                    }
                });

        if (!EmailAddressUtils.normalizedEmail(resp.payload().getUserEmail()).equals(userEmail)) {
            log.warning("Emails don't match?");
            throw new UnauthorizedException("Unauthorized");
        }
        return resp;
    }

    public static FriendResponse<Friend> insertFriend(final Friend friend,
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        
        final AtomicBoolean friended = new AtomicBoolean();
        final FriendResponse<Friend> response = 
                new Dao().withTransaction(new DbCall<FriendResponse<Friend>>() {
            @Override
            public FriendResponse<Friend> call(Objectify ofy) {
                String userEmail = email(user);
                String friendEmail = friend.getEmail();
                log.info(userEmail + " is considering inserting friend "
                        + friend.getEmail());
                friend.setUserEmail(userEmail);

                FriendingQuota quota = doGetOrCreateQuota(ofy, userEmail);
                if (quota == null) {
                    return failure();
                }
                Friend existing = getExistingFriendByEmail(ofy,
                        quota,
                        friendEmail);
                if (existing != null) {
                    log.warning("Found existing friend, updating instead: "
                            + existing.getEmail());
                    return doUpdateFriend(ofy, quota, friend, existing);
                }

                friended.set(Friend.Status.friend == friend.getStatus());

                if (friended.get()) {
                    if (!haveBeenFriendedBy(friend) &&
                            !quota.checkAndIncrementTotalFriended()) {
                        log.info("Friending quota exceeded");
                        return failure(quota);
                    }
                }

                log.info("Inserting friend");
                ofy.put(friend);
                ofy.put(quota);

                return success(quota, friend);
            }
        });
        if (friended.get()) {
            invite(friend);
        }
        return response;
    }

    public static FriendResponse<Friend> updateFriend(final Friend friend,
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        if (friend.getId() == null) {
            log.info("No ID on friend, treating as insert");
            return insertFriend(friend, user);
        }

        return new Dao().withTransaction(new DbCall<FriendResponse<Friend>>() {
            @Override
            public FriendResponse<Friend> call(Objectify ofy) {
                log.info("Updating friend...");
                String userEmail = email(user);
                FriendingQuota quota = doGetOrCreateQuota(ofy, userEmail);
                if (quota == null) {
                    return failure();
                }
                Friend existing = getExistingFriendById(ofy,
                        quota,
                        friend.getId());
                return doUpdateFriend(ofy, quota, friend, existing);
            }
        });
    }

    public static FriendResponse<Void> removeFriend(final Long id,
            final com.google.appengine.api.users.User user)
            throws UnauthorizedException {
        return new Dao().withTransaction(new DbCall<FriendResponse<Void>>() {
            @Override
            public FriendResponse<Void> call(Objectify ofy) {
                String userEmail = email(user);
                FriendingQuota quota = doGetOrCreateQuota(ofy, userEmail);
                if (quota == null) {
                    return failure();
                }
                LanternFriend existing = getExistingFriendById(ofy, quota, id);
                ofy.delete(existing);
                return success(quota, null);
            }
        });
    }

    public static FriendingQuota getOrCreateQuota(final String userEmail) {
        return new Dao().withTransaction(new DbCall<FriendingQuota>() {
            @Override
            public FriendingQuota call(Objectify ofy) {
                return doGetOrCreateQuota(ofy, userEmail);
            }
        });
    }

    public static void setMaxAllowed(final String userEmail,
            final int newMaxAllowed) {
        new Dao().withTransaction(new DbCall<Void>() {
            @Override
            public Void call(Objectify ofy) {
                FriendingQuota quota = doGetOrCreateQuota(ofy, userEmail);
                quota.setMaxAllowed(newMaxAllowed);
                ofy.put(quota);
                return null;
            }
        });
    }

    private static FriendResponse<Friend> doUpdateFriend(Objectify ofy,
            FriendingQuota quota,
            Friend friend,
            Friend existing) {
        if (existing == null) {
            log.warning("Didn't find existing friend to update");
            return success(quota, friend);
        } else {
            log.info("Found existing friend to update");
            Status priorStatus = existing.getStatus();
            boolean newlyFriended = Status.friend == friend.getStatus()
                    && Status.friend != priorStatus;
            if (newlyFriended) {
                if (!haveBeenFriendedBy(friend) &&
                        !quota.checkAndIncrementTotalFriended()) {
                    log.info("Friending quota exceeded");
                    return failure(quota);
                }
            }
            existing.setEmail(friend.getEmail());
            existing.setName(friend.getName());
            existing.setStatus(friend.getStatus());
            existing.setLastUpdated(friend.getLastUpdated());
            ofy.put(existing);
            ofy.put(quota);
            if (newlyFriended) {
                invite(friend);
            }
            return success(quota, existing);
        }
    }

    /**
     * Get an existing friend by their email address.
     * 
     * @param ofy
     *            the Objectify instance
     * @param quota
     *            The FriendingQuota for the logged-in user
     * @param user
     *            The user with the given friend.
     * @return The existing friend or <code>null</code> if no such friend
     *         exists.
     */
    private static LanternFriend getExistingFriendByEmail(Objectify ofy,
            FriendingQuota quota,
            String friendEmail) {
        return ofy.query(LanternFriend.class)
                .ancestor(quota)
                .filter("email", friendEmail.toLowerCase())
                .get();
    }

    private static LanternFriend getExistingFriendById(Objectify ofy,
            FriendingQuota quota,
            long id) {
        Key<FriendingQuota> parentKey =
                Key.create(FriendingQuota.class, quota.getEmail());
        Key<LanternFriend> key = Key.create(parentKey,
                LanternFriend.class, id);
        return ofy.find(key);
    }

    /**
     * Gets or creates a FriendingQuota for the given userEmail.
     * 
     * @param ofy
     * @param userEmail
     * @return
     */
    private static FriendingQuota doGetOrCreateQuota(Objectify ofy,
            String userEmail) {
        return doGetOrCreateQuota(ofy, userEmail, null);
    }
    
    /**
     * Gets or creates a FriendingQuota for the given userEmail.
     * 
     * @param ofy
     * @param userEmail
     * @param degree - if specified, the quota will be updated to reflect the
     * higher of its current value or the calculated value based on
     * {@link #maxFriendsForDegree(int)} using this degree
     * @return
     */
    private static FriendingQuota doGetOrCreateQuota(Objectify ofy,
            String userEmail, Integer degree) {
        Key<FriendingQuota> quotaKey =
                Key.create(FriendingQuota.class, userEmail);
        FriendingQuota quota = ofy.find(quotaKey);
        if (quota == null) {
            // Create a new quota for this user
            LanternUser user = ofy.find(LanternUser.class, userEmail);
            if (user == null) {
                log.info(String.format("User %s doesn't exist", userEmail));
                return null;
            }
            int maxFriends = 0;
            if (user.getGeneration() >= 1) {
                // Gen 1 and over users get some allowed friending ops
                maxFriends = maxFriendsForDegree(degree != null ? degree : user.getDegree());
            }
            quota = new FriendingQuota(userEmail, maxFriends);
            ofy.put(quota);
        } else if (degree != null) {
            int maxFriends = maxFriendsForDegree(degree);
            if (maxFriends > quota.getMaxAllowed()) {
                log.info(String
                        .format("Calculated quota for %1$s is higher than current, increasing to %2$s",
                                userEmail, maxFriends));
                quota.setMaxAllowed(maxFriends);
                ofy.put(quota);
            } else {
                log.info(String
                        .format("Existing quota for %1$s is high enough, leaving alone", userEmail));
            }
        }
        return quota;
    }
    
    /**
     * Recalculates the maxAllowed friending quota for a given userEmail.
     * 
     * @param ofy
     * @param userEmail
     * @param degree
     */
    public static void recalculateQuota(Objectify ofy, String userEmail, int degree) {
        doGetOrCreateQuota(ofy, userEmail, degree);
    }

    /**
     * Calculates the maximum friends for a given degree using the formula <a
     * href
     * ="https://www.wolframalpha.com/input/?i=plot+20%2F%28x%2B1%29%5E1.25">
     * 20/(x+1)^1.25</a>.
     * 
     * @param degree
     * @return
     */
    static int maxFriendsForDegree(int degree) {
        double M = LanternControllerConstants.MAX_MAX_FRIENDS;
        double x = degree;
        double f = LanternControllerConstants.MAX_FRIENDS_FACTOR;
        double max = M / (Math.pow((x + 1), f));
        return (int) Math.round(max);
    }

    /**
     * Normalizes email addresses.
     * 
     * @param user
     *            The user whose email address we want to normalize.
     * @return The normalized address.
     */
    private static String email(final User user) {
        return EmailAddressUtils.normalizedEmail(user.getEmail());
    }

    private static void invite(Friend friend) {
        // TODO: we should probably pop something on a task queue for processing
        // the invite after this transaction has succeeded
        log.info("Inviting friend");
        final Dao dao = new Dao();
        dao.addInviteAndApproveIfUnpaused(friend.getUserEmail(),
                                          friend.getEmail());
    }

    private static boolean haveBeenFriendedBy(final Friend friend) {
        return new Dao().withTransaction(new DbCall<Boolean>() {
            @Override
            public Boolean call(Objectify ofy) {
                String selfEmail = friend.getUserEmail();
                String friendEmail = friend.getEmail();
                log.info("Checking whether " + selfEmail
                        + " has already been friended by: " + friendEmail);
                FriendingQuota reverseQuota = doGetOrCreateQuota(ofy,
                        friendEmail);
                if (reverseQuota == null) {
                    log.info("Friend not found");
                    return false;
                }
                Friend reverse = getExistingFriendByEmail(ofy, reverseQuota,
                        selfEmail);
                log.info("Found? " + reverse);
                boolean haveBeenFriendedBy = reverse != null
                        && Status.friend == reverse.getStatus();
                log.info("Have been friended by?: " + haveBeenFriendedBy);
                return haveBeenFriendedBy;
            }
        });

    }

    private static <P> FriendResponse<P> success(FriendingQuota quota, P payload) {
        return new FriendResponse<P>(true, quota.getRemainingQuota(), payload);
    }

    private static <P> FriendResponse<P> failure() {
        return failure(null);
    }

    private static <P> FriendResponse<P> failure(FriendingQuota quota) {
        return new FriendResponse<P>(false,
                quota != null ? quota.getRemainingQuota() : 0, null);
    }
}
