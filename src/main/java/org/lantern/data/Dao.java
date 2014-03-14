package org.lantern.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.lantern.CensoredUtils;
import org.lantern.EmailAddressUtils;
import org.lantern.FallbackProxyLauncher;
import org.lantern.JsonUtils;
import org.lantern.LanternControllerConstants;
import org.lantern.MandrillEmailer;
import org.lantern.S3Config;
import org.lantern.admin.PendingInvites;
import org.lantern.data.Invite.Status;
import org.lantern.loggly.LoggerFactory;
import org.lantern.state.Friend;
import org.lantern.state.Mode;

import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.api.datastore.QueryResultIterable;
import com.google.appengine.api.datastore.QueryResultIterator;
import com.google.appengine.api.memcache.Expiration;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;
import com.googlecode.objectify.Key;
import com.googlecode.objectify.NotFoundException;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.Query;
import com.googlecode.objectify.util.DAOBase;

import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;


public class Dao extends DAOBase {

    private final transient Logger log = LoggerFactory.getLogger(getClass());

    private static final String[] countries = { "AF", "AX", "AL", "DZ", "AS",
            "AD", "AO", "AI", "AQ", "AG", "AR", "AM", "AW", "AU", "AT", "AZ",
            "BS", "BH", "BD", "BB", "BY", "BE", "BZ", "BJ", "BM", "BT", "BO",
            "BQ", "BA", "BW", "BV", "BR", "IO", "BN", "BG", "BF", "BI", "KH",
            "CM", "CA", "CV", "KY", "CF", "TD", "CL", "CN", "CX", "CC", "CO",
            "KM", "CG", "CD", "CK", "CR", "CI", "HR", "CU", "CW", "CY", "CZ",
            "DK", "DJ", "DM", "DO", "EC", "EG", "SV", "GQ", "ER", "EE", "ET",
            "FK", "FO", "FJ", "FI", "FR", "GF", "PF", "TF", "GA", "GM", "GE",
            "DE", "GH", "GI", "GR", "GL", "GD", "GP", "GU", "GT", "GG", "GN",
            "GW", "GY", "HT", "HM", "VA", "HN", "HK", "HU", "IS", "IN", "ID",
            "IR", "IQ", "IE", "IM", "IL", "IT", "JM", "JP", "JE", "JO", "KZ",
            "KE", "KI", "KP", "KR", "KW", "KG", "LA", "LV", "LB", "LS", "LR",
            "LY", "LI", "LT", "LU", "MO", "MK", "MG", "MW", "MY", "MV", "ML",
            "MT", "MH", "MQ", "MR", "MU", "YT", "MX", "FM", "MD", "MC", "MN",
            "ME", "MS", "MA", "MZ", "MM", "NA", "NR", "NP", "NL", "NC", "NZ",
            "NI", "NE", "NG", "NU", "NF", "MP", "NO", "OM", "PK", "PW", "PS",
            "PA", "PG", "PY", "PE", "PH", "PN", "PL", "PT", "PR", "QA", "RE",
            "RO", "RU", "RW", "BL", "SH", "KN", "LC", "MF", "PM", "VC", "WS",
            "SM", "ST", "SA", "SN", "RS", "SC", "SL", "SG", "SX", "SK", "SI",
            "SB", "SO", "ZA", "GS", "SS", "ES", "LK", "SD", "SR", "SJ", "SZ",
            "SE", "CH", "SY", "TW", "TJ", "TZ", "TH", "TL", "TG", "TK", "TO",
            "TT", "TN", "TR", "TM", "TC", "TV", "UG", "UA", "AE", "GB", "US",
            "UM", "UY", "UZ", "VU", "VE", "VN", "VG", "VI", "WF", "EH", "YE",
            "ZM", "ZW"
    };

    private static final String BYTES_EVER = "bytesEver";
    private static final String REQUESTS_PROXIED = "PROXIED_REQUESTS";
    private static final String DIRECT_BYTES = "DIRECT_BYTES";
    private static final String DIRECT_REQUESTS = "DIRECT_REQUESTS";
    private static final String CENSORED_USERS = "CENSORED_USERS";
    private static final String UNCENSORED_USERS = "UNCENSORED_USERS";
    private static final String TOTAL_USERS = "TOTAL_USERS";
    private static final String ONLINE = "online";
    private static final String NUSERS = "nusers";
    private static final String NPEERS = "npeers";
    private static final String EVER = "ever";
    private static final String GIVE = Mode.give.toString();
    private static final String GET = Mode.get.toString();
    private static final String BPS = "bps";
    private static final String GLOBAL = "global";

    private final ShardedCounterManager counterManager = new ShardedCounterManager();

    private final SettingsManager settingsManager = new SettingsManager();

    static final int TXN_RETRIES = 10;

    static {
        ObjectifyService.register(LanternUser.class);
        ObjectifyService.register(LanternInstance.class);
        ObjectifyService.register(Invite.class);
        ObjectifyService.register(PermanentLogEntry.class);
        ObjectifyService.register(TrustRelationship.class);
        ObjectifyService.register(LatestLanternVersion.class);
        ObjectifyService.register(LanternFriend.class);
        ObjectifyService.register(FriendingQuota.class);

        // Precreate all counters, if necessary
        ArrayList<String> counters = new ArrayList<String>();
        ArrayList<String> timedCounters = new ArrayList<String>();

        counters.add(dottedPath(GLOBAL, BYTES_EVER));
        counters.add(REQUESTS_PROXIED);
        counters.add(DIRECT_BYTES);
        counters.add(DIRECT_REQUESTS);
        counters.add(CENSORED_USERS);
        counters.add(UNCENSORED_USERS);
        counters.add(TOTAL_USERS);

        counters.add(dottedPath(GLOBAL, NUSERS, ONLINE));
        counters.add(dottedPath(GLOBAL, NUSERS, EVER));
        counters.add(dottedPath(GLOBAL, NPEERS, ONLINE, GIVE));
        counters.add(dottedPath(GLOBAL, NPEERS, ONLINE, GET));
        counters.add(dottedPath(GLOBAL, NPEERS, EVER, GIVE));
        counters.add(dottedPath(GLOBAL, NPEERS, EVER, GET));

        timedCounters.add(dottedPath(GLOBAL,BPS));

        for (final String country : countries) {
            counters.add(dottedPath(country, BYTES_EVER));
            counters.add(dottedPath(country, NUSERS, ONLINE));
            counters.add(dottedPath(country, NUSERS, EVER));

            counters.add(dottedPath(country, NPEERS, ONLINE, GIVE));
            counters.add(dottedPath(country, NPEERS, ONLINE, GET));
            counters.add(dottedPath(country, NPEERS, EVER, GIVE));
            counters.add(dottedPath(country, NPEERS, EVER, GET));
            timedCounters.add(dottedPath(country, BPS));
        }
        new ShardedCounterManager().initCounters(timedCounters, counters);
    }

    public Iterable<LanternUser> getAllUsers() {
        return ofy().query(LanternUser.class);
    }

    public void setInstanceAvailable(final String userId,
            final String instanceId, final String countryCode, final Mode mode,
            final String resource, final String listenHostAndPort,
            final boolean isFallbackProxy) {

        Boolean result = new RetryingTransaction<Boolean>() {
            @Override
            protected Boolean run(Objectify ofy) {
                final List<String> countersToUpdate = setInstanceAvailable(
                        ofy, userId, instanceId, countryCode, mode, resource,
                        listenHostAndPort, isFallbackProxy);
                ofy.getTxn().commit();
                // We only actually update the counters when we know the
                // transaction succeeded.  Since these affect the memcache
                // rather than the Datastore, there would be no way to roll
                // them back should this transaction fail.
                log.info("Transaction successful.");
                for (String counter : countersToUpdate) {
                    if (counter.startsWith("-")) {
                        counter = counter.substring(1);
                        log.info("Decrementing counter " + counter);
                        decrementCounter(counter);
                    } else {
                        log.info("Incrementing counter " + counter);
                        incrementCounter(counter);
                    }
                }
                return true;
            }
        }.run();

        if (result == null) {
            log.warning("Too much contention; giving up!");
        }
    }

    /**
     * @throws NotFoundException Shouldn't happen in production because
     * we'll make sure to populate this before it's called.
     */
    public LatestLanternVersion getLatestLanternVersion() throws NotFoundException {
        Key<LatestLanternVersion> key = new Key<LatestLanternVersion>(LatestLanternVersion.class, LatestLanternVersion.SINGLETON_KEY);
        return ofy().get(key);
    }

    public void setLatestLanternVersion(final LatestLanternVersion lanternVersion) {
        ofy().put(lanternVersion);
    }

    /**
     * @return a list of the counters that should be incremented as a
     * result of this instance having become available.
     */
    public List<String> setInstanceAvailable(Objectify ofy,
            String userId, final String instanceId, final String countryCode,
            final Mode mode, final String resource, String listenHostAndPort,
            boolean isFallbackProxy) {

        String modeStr = mode.toString();
        LanternUser user = ofy.find(LanternUser.class, userId);

        Key<LanternUser> parentKey = new Key<LanternUser>(LanternUser.class,
                userId);

        Key<LanternInstance> key = new Key<LanternInstance>(parentKey,
                LanternInstance.class, instanceId);

        LanternInstance instance = ofy.find(key);

        if (instance != null
            && StringUtils.equals(instance.getResource(), resource)) {
            log.info(String.format("We already knew '%1$s' was available",
                                   instanceId));
            return Collections.emptyList();
        }

        ArrayList<String> counters = new ArrayList<String>();
        boolean isNewInstance = instance == null;
        if (isNewInstance) {
            instance = new LanternInstance(instanceId, parentKey);
        }

        // Update properties common to new and updated instances
        instance.setResource(resource);
        instance.setListenHostAndPort(listenHostAndPort);
        instance.setFallbackProxy(isFallbackProxy);

        if (!isNewInstance) {
            log.info("Setting availability to true for " + userId + "/" + instanceId);
            if (instance.isAvailable()) {
                //handle mode changes
                if (instance.getMode() != mode) {
                    log.info("Mode change to " + modeStr);
                    counters.add(dottedPath(countryCode, NPEERS, ONLINE,
                                 modeStr));
                    counters.add(dottedPath(GLOBAL, NPEERS, ONLINE, modeStr));
                    //and decrement the old counters
                    String oldModeStr = instance.getMode().toString();
                    counters.add("-" + dottedPath(countryCode, NPEERS, ONLINE,
                                 oldModeStr));
                    counters.add("-" + dottedPath(GLOBAL, NPEERS, ONLINE,
                                 oldModeStr));
                    instance.setMode(mode);
                    log.info("Finished updating datastore...");
                }

            } else {
                log.info("Instance exists but was unavailable.");
                updateStatsForNewlyAvailableInstance(ofy, user, instance,
                        countryCode, mode, counters);
            }
        } else {
            log.info("Could not find instance!!");

            instance.setUser(userId);
            // The only counter that we need handling differently for new
            // instances is the global peers ever.
            counters.add(dottedPath(GLOBAL, NPEERS, EVER, modeStr));
            updateStatsForNewlyAvailableInstance(ofy, user, instance,
                    countryCode, mode, counters);
        }

        instance.setLastUpdated(new Date());
        ofy.put(instance);

        return counters;
    }

    /**
     * <p>
     * Possibly modifies the user, instance and the counters (via the `counters`
     * list).
     * </p>
     * 
     * <p>
     * Note - the instance is not saved with a call to put() because that is
     * handled by the calling function.
     * </p>
     */
    private void updateStatsForNewlyAvailableInstance(Objectify ofy,
            LanternUser user, LanternInstance instance,
            final String countryCode, final Mode mode,
            ArrayList<String> counters) {
        //handle the online counters
        String modeStr = mode.toString();
        counters.add(dottedPath(countryCode, NPEERS, ONLINE, modeStr));
        counters.add(dottedPath(GLOBAL, NPEERS, ONLINE, modeStr));
        //and the ever-seen
        if (!instance.getSeenFromCountry(countryCode)) {
            instance.addSeenFromCountry(countryCode);
            counters.add(dottedPath(countryCode, NPEERS, EVER, modeStr));
        }
        if (!user.countrySeen(countryCode)){
            counters.add(dottedPath(countryCode, NUSERS, EVER));
        }
        //notice that we check for any signed in before we set this instance
        //available

        //we can do an ancestor query to figure out how many remaining
        //instances there are

        Query<LanternInstance> query = signedInInstanceQuery(ofy, user.getId());
        boolean anyInstancesSignedIn = query.count() > 0;

        if (!anyInstancesSignedIn) {
            counters.add(dottedPath(GLOBAL, NUSERS, ONLINE));
            counters.add(dottedPath(countryCode, NUSERS, ONLINE));
        }
        instance.setCurrentCountry(countryCode);
        instance.setMode(mode);
        instance.setAvailable(true);
        ofy.put(user);
        log.info("Finished updating datastore...");
    }

    /**
     * Update fallbackProxyUserId and fallbackProxy if we got a non-default
     * fallbackProxyHostAndPort.
     */
    public void processFallbackProxyHostAndPort(final String userId,
                                                final String hostAndPort) {
        log.info("Processing host:port for " + userId);
        if (StringUtils.isBlank(hostAndPort)) {
            log.info("No hostAndPort.");
            return;
        }
        if (LanternControllerConstants.DEFAULT_FALLBACK_HOST_AND_PORT
                .equals(hostAndPort)) {
            // This may be caused by a wiped ~/.lantern folder.  Don't
            // treat this as a valid fallbackProxyUserId.
            log.info("Ignoring default fallback.");
            return;
        }

        RetryingTransaction<Void> txn = new RetryingTransaction<Void>() {
            @Override
            protected Void run(Objectify txnOfy) {
                LanternUser user = txnOfy.find(LanternUser.class, userId);

                // The only point of the transaction is to protect the read
                // and write of the user entry, and to make sure that we only
                // update invites once.  We don't need to include the query
                // in the transaction for this.
                Objectify queryOfy = ofy();
                List<LanternInstance> matches
                    = queryOfy.query(LanternInstance.class)
                        .filter("listenHostAndPort =", hostAndPort).list();
                if (matches.size() == 0) {
                    logPermanently("fallbackNotFound:" + userId,
                                   userId + "'s fallback host and port "
                                   + hostAndPort
                                   + " don't match any fallback proxy.");
                    return null;
                } else if (matches.size() > 1) {
                    log.severe(matches.size() + "instances found with ip:port"
                               + hostAndPort + "!");
                    // Bail rather than pair user with someone they don't
                    // trust.
                    return null;
                }

                LanternInstance instance = matches.get(0);

                if (instance.isFallbackProxyShutdown()) {
                    logPermanently("obsoleteHostPort:" + userId,
                                   userId + " has obsolete fallback proxy "
                                   + instance.getId() + " of user "
                                   + instance.getUser());
                    return null;
                }

                String oldFpuid = user.getFallbackProxyUserId();
                String newFpuid = instance.getUser();
                boolean fpUserChanged = !newFpuid.equals(oldFpuid)
                                        // If a user is their own fallback
                                        // user, we don't override that.
                                        && !userId.equals(oldFpuid);
                if (fpUserChanged) {
                    user.setFallbackProxyUserId(newFpuid);
                    log.info("Set " + userId + "'s fallbackProxyUserId to "
                             + newFpuid + " (was " + oldFpuid + ")");
                }

                Key<LanternInstance> oldFpKey = user.getFallbackProxy();
                Key<LanternInstance> newFpKey
                    = getInstanceKey(newFpuid, instance.getId());
                boolean fpChanged = !newFpKey.equals(oldFpKey);
                if (fpChanged) {
                    user.setFallbackProxy(newFpKey);
                    log.info("Set " + userId + "'s fallbackProxy to "
                             + newFpKey + " (was " + oldFpKey + ")");
                }

                if (fpUserChanged || fpChanged) {
                    txnOfy.put(user);
                    txnOfy.getTxn().commit();
                }

                return null;
            }
        };
        txn.run();
        if (txn.failed()) {
            log.warning("Transaction failed!");
        }
    }


    /**
     * Allow user to start fallback proxies.
     *
     * If the user is already a fallbackProxyUserId this does nothing.
     * Otherwise, a proxy will be launched next time we approve an invite by
     * this user.
     */
    //XXX: as of this writing nobody is calling this.  This is meant to be
    // called from some admin page.
    public void makeFallbackProxyUser(final String userId) {
        RetryingTransaction<Void> txn = new RetryingTransaction<Void>() {
            protected Void run(Objectify ofy) {
                LanternUser user = ofy.find(LanternUser.class, userId);
                user.setFallbackProxyUserId(userId);
                ofy.put(user);
                ofy.getTxn().commit();
                return null;
            }
        };
        txn.run();
        if (txn.failed()) {
            throw new RuntimeException("Transaction failed!");
        }
    }

    private static String dottedPath(String ... strings) {
        return StringUtils.join(strings, ".");
    }


    public boolean alreadyInvitedBy(String sponsor, String guest) {
        Objectify ofy = ofy();
        return alreadyInvitedBy(ofy, sponsor, guest);
    }

    /**
     * Deletes the friend of the specified user.
     * 
     * @param user The email address of the user.
     * @param friend The email address of the friend of that user.
     */
    public void deleteFriend(final String user, final String friend) {
        final LanternUser lu = getUser(user);
        final QueryResultIterable<TrustRelationship> children = 
                getChildren(lu, TrustRelationship.class);

        for (final TrustRelationship tr : children) {
            if (tr.getId().equals(friend)) {
                System.out.println("Found friend: "+tr.getId());
                final Objectify ofy = ofy();
                ofy.delete(tr);
                break;
            }
        }
    }

    /**
     * Lists the friends of the specified user.
     * 
     * @param user The email address of the user to list friends for.
     * @return The email addresses of those friends.
     */
    public Collection<String> listFriends(final String user) {
        final LanternUser lu = getUser(user);
        final QueryResultIterable<TrustRelationship> children = 
            getChildren(lu, TrustRelationship.class);

        final Collection<String> friends = new ArrayList<String>();
        for (final TrustRelationship tr : children) {
            friends.add(tr.getId());
        }
        return friends;
    }

    /**
     * Utility method to get the children of a given ancestor.
     * 
     * @param parent The parent object.
     * @param clazz The class of the children.
     * @return The iterable query results.
     */
    private <T, V> QueryResultIterable<T> getChildren(final V parent, 
        final Class<T> clazz) {
        final Objectify ofy = ofy();
        return ofy.query(clazz).ancestor(ofy.getFactory().getKey(parent)).fetch();
    }

    /**
     * Returns true if the invite was added, false if it wasn't (because it
     * already existed, for instance)
     */
    public boolean addInvite(String inviterId, String inviteeEmail,
            final String emailTemplate) {

        final String normInviterId = EmailAddressUtils.normalizedEmail(inviterId);
        final String normInviteeEmail = EmailAddressUtils.normalizedEmail(inviteeEmail);

        Boolean result = new RetryingTransaction<Boolean>() {
            @Override
            protected Boolean run(Objectify ofy) {
                LanternUser inviter = ofy.find(LanternUser.class, normInviterId);
                if (inviter == null) {
                    log.warning("Could not find inviterId sending invite: " + normInviterId);
                    return false;
                }

                if (alreadyInvitedBy(ofy, normInviterId, normInviteeEmail)) {
                    log.info("Not re-sending e-mail since user is already invited");
                    return false;
                }

                String fpuid = inviter.getFallbackProxyUserId();

                if (fpuid == null) {
                    throw new RuntimeException(
                            "This should never be true anymore!");
                }

                Invite invite = new Invite(normInviterId, normInviteeEmail, fpuid, emailTemplate);
                ofy.put(invite);

                ofy.getTxn().commit();
                log.info("Successfully committed new invite.");
                return true;
            }
        }.run();

        if (result == null) {
            log.warning("Too much contention trying to add invite; gave up.");
            return false;
        }

        return result;
    }

    public void addInviteAndApproveIfUnpaused(String inviterId, String inviteeId) {
        addInviteAndApproveIfUnpaused(inviterId, inviteeId, "invite-notification");
    }

    public void addInviteAndApproveIfUnpaused(
            String inviterId, String inviteeId, String emailTemplate) {
        if (addInvite(inviterId, inviteeId, emailTemplate)) {
            if (areInvitesPaused()) {
                log.info("Invite held for approval.");
            } else {
                FallbackProxyLauncher.authorizeInvite(inviterId, inviteeId);
            }
        }
    }


    /**
     * See https://github.com/getlantern/lantern-controller#setting-up-a-test-lantern-controller
     */
    public void createInitialUser(final String email) {
        if (true) {
            log.info("Flip the condition above if you really mean to run this.");
        } else {
            final Objectify ofy = ofy();
            final LanternUser user = new LanternUser("_pants@getlantern.org");
            user.setDegree(1);
            user.setEverSignedIn(true);
            user.setSponsor("lanternfriend@gmail.com");
            ofy.put(user);
            log.info("createInitialUser succeeded.");
        }
    }

    public LanternUser createInvitee(final String inviteeEmail,
                                     final String inviterId,
                                     final String fallbackProxyUserId,
                                     final String fallbackInstanceId) {
        // Although the friends code should have made sure to normalize the
        // inviteeEmail, let's double-check this just to be on the safe side.
        final String normalizedInviteeEmail
                = EmailAddressUtils.normalizedEmail(inviteeEmail);

        log.info("Making sure " + normalizedInviteeEmail
                 + ", invited by " + inviterId + ", has been created.");
        final LanternUser inviter = findUser(inviterId);
        RetryingTransaction<LanternUser> txn
            = new RetryingTransaction<LanternUser>() {
            @Override
            public LanternUser run(Objectify ofy) {
                LanternUser invitee = ofy.find(LanternUser.class,
                                               normalizedInviteeEmail);
                boolean anyChange = false;
                if (invitee == null) {
                    log.info("Adding invitee to database");
                    invitee = new LanternUser(normalizedInviteeEmail);
                    invitee.setDegree(inviter.getDegree() + 1);
                    invitee.setSponsor(inviter.getId());
                    invitee.setFallbackProxyUserId(fallbackProxyUserId);
                    invitee.setFallbackProxy(
                            getInstanceKey(fallbackProxyUserId,
                                           fallbackInstanceId));
                    // Not bothering with a constant because any non-null value
                    // will do.
                    log.info("Successfully committed attempt to add invitee.");
                    anyChange = true;
                }
                if (invitee.getConfigFolder() == null) {
                    invitee.setConfigFolder("pending");
                    enqueueConfigAndWrappers(normalizedInviteeEmail);
                    anyChange = true;
                }
                if (anyChange) {
                    ofy.put(invitee);
                    ofy.getTxn().commit();
                } else {
                    log.info("Invitee exists and wrappers have been requested; nothing to do here.");
                }
                return invitee;
            }
        };
        LanternUser invitee = txn.run();
        if (txn.failed()) {
            throw new RuntimeException(
                    "Transaction failed trying to create "
                    + normalizedInviteeEmail);
        }
        return invitee;
    }

    public boolean alreadyInvitedBy(Objectify ofy, final String inviterEmail,
            final String inviteeEmail) {
        final Invite invite = getInvite(ofy, inviterEmail, inviteeEmail);
        return invite != null;
    }

    public Invite getInvite(Objectify ofy, final String inviterEmail,
            final String inviteeEmail) {
        String id = Invite.makeId(inviterEmail, inviteeEmail);
        Key<LanternUser> bogusParentKey
            = new Key<LanternUser>(LanternUser.class, id);
        return ofy.find(new Key<Invite>(bogusParentKey, Invite.class, id));
    }

    public boolean isInvited(final String email) {
        final Objectify ofy = ofy();
        final LanternUser user = ofy.find(LanternUser.class, email);
        return user != null;
    }

    public void updateLastAccessed(final String email) {
        Boolean result = new RetryingTransaction<Boolean>() {
            @Override
            public Boolean run(Objectify ofy) {
                final LanternUser user = ofy.find(LanternUser.class, email);
                if (user != null) {
                    user.setLastAccessed(new Date());
                    ofy.put(user);
                }
                ofy.getTxn().commit();
                return true;
            }
        }.run();
        if (result == null) {
            log.warning("Too much contention!");
        }
    }

    public boolean updateUser(final String userId, final long directRequests,
            final long directBytes, final long requestsProxied,
            final long bytesProxied, final String countryCode,
            final String name, final Mode mode) {
        log.info("Updating user with stats: dr: " + directRequests + " db: "
                + directBytes + " bytesProxied: " + bytesProxied);
        Boolean result = new RetryingTransaction<Boolean>() {
            @Override
            public Boolean run(Objectify ofy) {
                LanternUser user = ofy.find(LanternUser.class, userId);
                boolean isUserNew = (user == null);
                if (isUserNew) {
                    log.info("Could not find user!!");
                    user = new LanternUser(userId);
                    user.setName(name);
                }
                user.setBytesProxied(user.getBytesProxied() + bytesProxied);
                user.setRequestsProxied(user.getRequestsProxied()
                        + requestsProxied);
                user.setDirectBytes(user.getDirectBytes() + directBytes);
                user.setDirectRequests(user.getDirectRequests()
                        + directRequests);

                ofy.put(user);
                ofy.getTxn().commit();
                log.info("Transaction successful.");

                // Only increment counters on success.
                incrementCounter(dottedPath(countryCode, BYTES_EVER), bytesProxied);
                incrementCounter(dottedPath(GLOBAL, BYTES_EVER), bytesProxied);

                incrementCounter(dottedPath(countryCode, BPS), bytesProxied);
                incrementCounter(dottedPath(GLOBAL, BPS), bytesProxied);

                incrementCounter(REQUESTS_PROXIED, requestsProxied);
                incrementCounter(DIRECT_BYTES, directBytes);
                incrementCounter(DIRECT_REQUESTS, directRequests);
                if (isUserNew) {
                    counterManager.increment(TOTAL_USERS);
                    if (CensoredUtils.isCensored(countryCode)) {
                        incrementCounter(CENSORED_USERS);
                    } else {
                        log.info("Incrementing uncensored count");
                        incrementCounter(UNCENSORED_USERS);
                    }
                    incrementCounter(countryCode + ".nusers.ever");
                }
                return isUserNew;
            }
        }.run();
        if (result == null) {
            // The caller expects some return value.
            throw new RuntimeException("Too much contention!");
        }
        return result;
    }

    private void decrementCounter(String counter) {
        counterManager.decrement(counter);
    }

    private void incrementCounter(String counter) {
        counterManager.increment(counter);
    }

    private void incrementCounter(String counter, long count) {
        counterManager.increment(counter, count);
    }

    public String getStats() {
        final Map<String, Object> data = new HashMap<String, Object>();
        add(data, REQUESTS_PROXIED);
        add(data, DIRECT_BYTES);
        add(data, DIRECT_REQUESTS);
        add(data, CENSORED_USERS);
        add(data, UNCENSORED_USERS);
        add(data, dottedPath(GLOBAL, NUSERS, ONLINE));
        add(data, dottedPath(GLOBAL, NUSERS, EVER));

        add(data, dottedPath(GLOBAL, NPEERS, EVER, GIVE));
        add(data, dottedPath(GLOBAL, NPEERS, EVER, GET));
        add(data, dottedPath(GLOBAL, NPEERS, ONLINE, GIVE));
        add(data, dottedPath(GLOBAL, NPEERS, ONLINE, GET));
        add(data, dottedPath(GLOBAL, BPS));
        add(data, dottedPath(GLOBAL, BYTES_EVER));

        final Map<String, Object> countriesData = new HashMap<String, Object>();
        for (final String country : countries) {
            add(countriesData, dottedPath(country, BPS));
            add(countriesData, dottedPath(country, BYTES_EVER));
            add(countriesData, dottedPath(country, NUSERS, ONLINE));
            add(countriesData, dottedPath(country, NUSERS, EVER));

            add(countriesData, dottedPath(country, NPEERS, ONLINE, GIVE));
            add(countriesData, dottedPath(country, NPEERS, ONLINE, GET));
            add(countriesData, dottedPath(country, NPEERS, EVER, GIVE));
            add(countriesData, dottedPath(country, NPEERS, EVER, GET));
        }
        data.put("countries", countriesData);
        return JsonUtils.jsonify(data);
    }

    /**
     * Take a counter name of the form a.b.c...y.z, and add it to data
     * recursively, so that data will contain an entry for a which contains an
     * entry for b, and so on down to the last level. The value of the counter
     * with the full dotted name will be put into z entry of the y container.
     *
     * @param data
     * @param key a counter name in the form a.b.c...
     */
    private void add(final Map<String, Object> data, final String key) {
        add(data, key, key);
    }

    @SuppressWarnings("unchecked")
    private void add(final Map<String, Object> data, final String key, final String counterName) {
        if (key.contains(".")) {
            String[] parts = key.split("\\.", 2);
            String containerName = parts[0];
            String remainder = parts[1];
            Object existing = data.get(containerName);
            Map<String, Object> container;
            if (existing == null) {
                container = new HashMap<String, Object>();
                data.put(containerName, container);
            } else {
                container = (Map<String, Object>) existing;
            }
            add(container, remainder, counterName);
        } else {
            long count = counterManager.getCount(counterName);
            data.put(key, count);
        }
    }

    public void whitelistAdditions(final Collection<String> whitelistAdditions,
        final String countryCode) {
        if (whitelistAdditions == null) {
            return;
        }
        for (final String url : whitelistAdditions) {
            final WhitelistEntry entry = new WhitelistEntry();
            entry.setUrl(url);
            entry.setCountryCode(countryCode);
        }
    }

    public void whitelistRemovals(final Collection<String> whitelistRemovals,
        final String countryCode) {
        if (whitelistRemovals == null) {
            return;
        }
        for (final String url : whitelistRemovals) {
            final WhitelistRemovalEntry entry = new WhitelistRemovalEntry();
            entry.setUrl(url);
            entry.setCountryCode(countryCode);
        }
    }

    public void signedIn(final String email, final String language) {
        Boolean result = new RetryingTransaction<Boolean>() {
            @Override
            public Boolean run(Objectify ofy) {
                LanternUser user = ofy.find(LanternUser.class, email);
                if (!user.isEverSignedIn()) {
                    log.info("This is a new user.");
                    user.setEverSignedIn(true);
                    ofy.put(user);
                    ofy.getTxn().commit();
                    // Increment after committing, because memcache is not
                    // rolled back if the transaction fails.
                    log.info("Incrementing global.nusers.ever.");
                    incrementCounter(dottedPath(GLOBAL, NUSERS, EVER));
                    return true;
                }
                return false;
            }
        }.run();

        if (result != null && result) {
            MandrillEmailer.addEmailToUsersList(email, language);
        }
    }

    public void setInstanceUnavailable(final String userId,
            final String resource) {
        Boolean result = new RetryingTransaction<Boolean>() {
            @Override
            public Boolean run(Objectify ofy) {
                final ArrayList<String> countersToUpdate = setInstanceUnavailable(
                        ofy, userId, resource);
                ofy.getTxn().commit();
                // We only actually update the counters when we know the
                // transaction succeeded. Since these affect the memcache
                // rather than the Datastore, there would be no way to roll
                // them back should this transaction fail.
                for (String counter : countersToUpdate) {
                    if (counter.startsWith("-")) {
                        log.info("Decrementing counter " + counter);
                        decrementCounter(counter.substring(1));
                    } else {
                        log.info("Incrementing counter " + counter);
                        incrementCounter(counter);
                    }
                }
                return true;
            }
        }.run();
        if (result == null) {
            log.warning("Too much contention; giving up!");
        }
    }

    public ArrayList<String> setInstanceUnavailable(final Objectify ofy,
            final String userId, final String resource) {
        ArrayList<String> counters = new ArrayList<String>();

        final Key<LanternUser> parent = new Key<LanternUser>(LanternUser.class, userId);

        // for unavailable, we search by resource, because we do not have the
        // instance id
        final Query<LanternInstance> searchByResource = ofy
                .query(LanternInstance.class).ancestor(parent)
                .filter("resource", resource);
        final LanternInstance instance = searchByResource.get();
        if (instance == null) {
            log.warning("Instance " + userId + "/" + resource + " not found.");
            return counters;
        }
        if (instance.isAvailable()) {
            log.info("Decrementing online count");
            instance.setAvailable(false);

            String modeStr = instance.getMode().toString();
            String countryCode = instance.getCurrentCountry();

            counters.add("-" + dottedPath(GLOBAL, NPEERS, ONLINE, modeStr));
            counters.add("-" + dottedPath(countryCode, NPEERS, ONLINE, modeStr));

            Query<LanternInstance> query = signedInInstanceQuery(ofy, userId);

            //we compare against 1 here because we have not yet
            //saved the current instance
            boolean anyInstancesSignedIn = query.count() > 1;

            if (!anyInstancesSignedIn) {
                log.info("Decrementing online user count");
                counters.add("-" + dottedPath(GLOBAL, NUSERS, ONLINE));
                counters.add("-" + dottedPath(countryCode, NUSERS, ONLINE));
            }

            ofy.put(instance);
        }
        return counters;
    }

    private Query<LanternInstance> signedInInstanceQuery(Objectify ofy,
            String userId) {
        Key<LanternUser> parentKey = new Key<LanternUser>(LanternUser.class,
                userId);
        Query<LanternInstance> query = ofy.query(LanternInstance.class)
                .ancestor(parentKey).filter("available", true);
        return query;
    }

    /**
     * @return the old one.
     */
    public String setFallbackForNewInvitees(
            final String userId, final String newFallback) {
        RetryingTransaction<String> txn = new RetryingTransaction<String>() {
            @Override
            public String run(Objectify ofy) {
                LanternUser user = ofy.find(LanternUser.class, userId);
                String old = user.getFallbackForNewInvitees();
                user.setFallbackForNewInvitees(newFallback);
                ofy.put(user);
                ofy.getTxn().commit();
                return old;
            }
        };

        String old = txn.run();
        if (txn.failed()) {
            throw new RuntimeException(
                    "Gave up because of too much contention!");
        }
        return old;
    }

    public Collection<Invite> getAuthorizedInvitesForFallbackProxyUserId(
            String userId) {
        int maxInvites = getMaxInvitesPerProxy();
        return ofy().query(Invite.class)
                    .filter("status", Invite.Status.authorized)
                    .filter("fallbackProxyUser", userId)
                    .limit(maxInvites)
                    .list();
    }

    private boolean emailsMatch(final String one, final String other) {
        return one.trim().equalsIgnoreCase(other.trim());
    }

    public int getUserCount() {
        Objectify ofy = ofy();
        return ofy.query(LanternUser.class).filter("everSignedIn", true).count();
    }

    public void forgetEveryoneSignedIn() {
        // One-shot job so lantern-controller reckons old users in the global
        // ever counter.  Uncomment and run from RemoteAPI for fun and evil.
        // You need to uncomment additional methods in LanternUser.java for
        // this to work.
        log.warning("The Button is disabled!"
                    + "  Uncomment and redeploy if you really mean it.");
        /*
        Objectify ofy = ofy();
        for (LanternUser user : ofy.query(LanternUser.class)) {
            user.resetInstancesSignedIn();
            user.setEverSignedIn(false);
            // Uncomment this method in LanternUsers too.
            user.resetCountryCodes();
            ofy.put(user);
        }
        ofy.delete(ofy.query(LanternInstance.class));
        log.warning("Sign-in data reset.");
        */
    }

    public void logPermanently(String key, String contents) {
        Objectify ofy = ofy();
        if (ofy.find(PermanentLogEntry.class, key) == null) {
            ofy().put(new PermanentLogEntry(key, contents));
            log.warning("Permanent log: " + contents);
        } else {
            log.warning("Already logged: " + contents);
        }
    }

    public boolean areInvitesPaused() {
        return settingsManager.getBoolean("invitesPaused");
    }

    public void setInvitesPaused(boolean paused) {
        settingsManager.set("invitesPaused", "" + paused);
    }

    public void setMaxInvitesPerProxy(int n) {
        settingsManager.set("maxInvitesPerProxy", "" + n);
    }

    public int getMaxInvitesPerProxy() {
        try {
            return Integer.parseInt(settingsManager.get("maxInvitesPerProxy"));
        } catch (NumberFormatException e) {
            // Pulled out of thin air; set this to a more empirical estimate
            // when we actually gather the data.
            return 200;
        }
    }

    public boolean isAdmin(String userId) {
        Objectify ofy = ofy();
        LanternUser user = ofy.find(LanternUser.class, userId);
        return user.getDegree() == 0;
    }

    public List<LegacyFriend> syncFriends(final String userId,
            final LegacyFriends clientFriends) {
        List<LegacyFriend> result = new RetryingTransaction<List<LegacyFriend>>() {
            @Override
            public List<LegacyFriend> run(Objectify ofy) {
                List<LegacyFriend> updated = new ArrayList<LegacyFriend>();
                Key<LanternUser> parentKey = new Key<LanternUser>(LanternUser.class,
                        userId);
                Collection<LegacyFriend> clientFriendList = clientFriends.getFriends();

                Query<TrustRelationship> relationships = ofy.query(TrustRelationship.class).ancestor(
                        parentKey);
                Map<String, TrustRelationship> relationshipSet = new HashMap<String, TrustRelationship>();
                for (TrustRelationship relationship : relationships) {
                    relationshipSet.put(relationship.getId(), relationship);
                }
                boolean save = false;
                for (LegacyFriend friend : clientFriendList) {
                    String id = friend.getEmail();
                    TrustRelationship trust = relationshipSet.get(id);
                    if (trust == null) {
                        //controller has never heard of this relationship
                        trust = new TrustRelationship(parentKey, friend);
                        ofy.put(trust);
                        save = true;
                    } else if (trust.isNewerThan(friend)) {
                        //controller version is newer
                        friend.setLastUpdated(trust.getLastUpdated());
                        friend.setStatus(trust.getStatus());
                        updated.add(friend);
                    } else if (trust.update(friend)) {
                        //client version is newer
                        ofy.put(trust);
                        save = true;
                    }
                    relationshipSet.remove(id);
                }
                //now handle the relationships that the controller is aware of
                //but the client is not
                for (TrustRelationship relationship: relationshipSet.values()) {
                    LegacyFriend friend = new LegacyFriend(relationship.getId());
                    friend.setLastUpdated(relationship.getLastUpdated());
                    friend.setStatus(relationship.getStatus());
                    updated.add(friend);
                }
                //TODO In the long run, we want to save the last update in LanternUser
                //for efficiently checking if we need to send the complete list of friend changes
                if (save) {
                    ofy.getTxn().commit();
                }
                return updated;
            }
        }.run();
        if (result != null) {
            return result;
        } else {
            return Collections.emptyList();
        }
    }

    public void syncFriend(final String userId, final LegacyFriend clientFriend) {
        //just sync a single friend up from the client
        //if the client's version is the less up-to-date version,
        //we'll handle that elsewhere
        Boolean result = new RetryingTransaction<Boolean>() {
            @Override
            public Boolean run(Objectify ofy) {
                String id = clientFriend.getEmail();
                Key<LanternUser> parentKey = new Key<LanternUser>(LanternUser.class,
                        userId);
                Key<TrustRelationship> key = new Key<TrustRelationship>(parentKey, TrustRelationship.class, id);
                TrustRelationship trust = ofy.find(key);
                if (trust == null) {
                    trust = new TrustRelationship(parentKey, clientFriend);
                    ofy.put(trust);
                    ofy.getTxn().commit();
                } else if (trust.update(clientFriend)) {
                    ofy.getTxn().commit();
                }

                return true;
            }
        }.run();
        if (result == null) {
            log.warning("Too much transaction contention syncing friend");
        }
    }

    public boolean setInviteStatus(final String inviterEmail,
            final String inviteeEmail,
            final Invite.Status toStatus) {
        return setInviteStatus(inviterEmail, inviteeEmail, null, toStatus);
    }
    
    /**
     * <p>
     * Set the status of the invite identified by the <code>inviterEmail</code>
     * and <code>inviteeEmail</code> to the given <code>toStatus</code> if its
     * current status either equals the given <code>fromStatus</code> or if the
     * given <code>fromStatus</code> is null.
     * </p>
     * 
     * <p>
     * If the invite no longer exists, the status isn't updated (obviously).
     * </p>
     * 
     * @param inviterEmail
     * @param inviteeEmail
     * @param fromStatus
     * @param toStatus
     * @return true if the status was updated, false otherwise
     */
    public boolean setInviteStatus(final String inviterEmail,
                                final String inviteeEmail,
                                final Invite.Status fromStatus,
                                final Invite.Status toStatus) {
        RetryingTransaction<Boolean> tx = new RetryingTransaction<Boolean>() {
            @Override
            protected Boolean run(Objectify ofy) {
                Invite invite = getInvite(ofy, inviterEmail, inviteeEmail);
                if (invite != null) {
                    if (fromStatus == null || fromStatus == invite.getStatus()) {
                        invite.setStatus(toStatus);
                        ofy.put(invite);
                        ofy.getTxn().commit();
                        return true;
                    }
                }
                return false;
            }
        };
        String desc = "Invite from " + inviterEmail
                      + " to " + inviteeEmail
                      + ": setting status to " + toStatus;
        Boolean statusUpdated = tx.run();
        if (tx.failed()) {
            throw new RuntimeException(desc + " -- transaction failed!");
        } else {
            log.info(desc + ": " + (statusUpdated ? "OK" : "nay!"));
        }
        return statusUpdated;
    }

    public PendingInvites getPendingInvites(String cursorStr) {
        log.info("getPendingInvites");
        Objectify ofy = ofy();
        Query<Invite> query = ofy.query(Invite.class).filter("status",
                Status.queued);

        if (!StringUtils.isEmpty(cursorStr)) {
            log.info("setting cursor from " + cursorStr);
            query.startCursor(Cursor.fromWebSafeString(cursorStr));
        }

        PendingInvites result = new PendingInvites();
        QueryResultIterator<Invite> iterator = query.iterator();
        int i = 0;
        while (iterator.hasNext() && i++ < 100) {
            Invite invite = iterator.next();
            result.addInvite(invite);
        }
        log.info("invites = " + i);
        if (iterator.hasNext()) {
            log.info("new cursor");
            Cursor cursor = iterator.getCursor();
            result.setCursor(cursor.toWebSafeString());
        }
        return result;
    }

    public int deletePendingInvites(final String[] ids) {
        int totalDeleted = 0;
        for (String id : ids) {
            totalDeleted += deletePendingInvite(id);
        }
        return totalDeleted;
    }

    private int deletePendingInvite(final String id) {
        return new RetryingTransaction<Integer>() {
            @Override
            protected Integer run(Objectify ofy) {
                int deleted = 0;
                String[] parsedId = Invite.parseId(id);
                Invite invite = getInvite(ofy, parsedId[0], parsedId[1]);
                if (invite != null) {
                    if (invite.getStatus() == Status.queued) {
                        ofy.delete(invite);
                        deleted = 1;
                    } else {
                        log.info(
                            String.format(
                                "Refusing to delete invite %1$s in status %2$s",
                                id, invite.getStatus()));
                    }
                } else {
                    log.info(String.format("Unable to find invite: %1$s",
                                           id));
                }
                ofy.getTxn().commit();
                return deleted;
            }
        }.run();
    }

    //XXX: review who's calling this and for what; the memcache copy is not
    // maintained nearly well enough for most uses.
    public LanternUser getUser(String email) {
        MemcacheService cache = MemcacheServiceFactory.getMemcacheService();
        LanternUser user = (LanternUser) cache.get("user " + email);
        if (user == null) {
            user = findUser(email);
            if (user != null) {
                cache.put("user" + email, user, Expiration.byDeltaSeconds(1000));
            }
        }
        return user;
    }

    public LanternUser findUser(String email) {
        return ofy().find(LanternUser.class, email);
    }
    
    public List<LanternUser> findUsersByIds(Collection<String> ids) {
        if (ids == null || ids.size() == 0) {
            return Collections.EMPTY_LIST;
        } else {
            return ofy().query(LanternUser.class)
                    .filter("id IN", ids)
                    .list();
        }
    }

    public LanternInstance findInstance(Key<LanternInstance> key) {
        return ofy().find(key);
    }

    public LanternInstance findInstance(String userId, String instanceId) {
        return findInstance(ofy(), userId, instanceId);
    }

    private LanternInstance findInstance(
            Objectify ofy,
            String userId,
            String instanceId) {
        return ofy.find(getInstanceKey(userId, instanceId));
    }

    private Key<LanternUser> getUserKey(String userId) {
        return new Key<LanternUser>(LanternUser.class, userId);
    }

    public Key<LanternInstance> getInstanceKey(String userId, String instanceId) {
        return new Key<LanternInstance>(getUserKey(userId),
                                        LanternInstance.class,
                                        instanceId);
    }

    /**
     * Find or create the instance and set its accessData.
     */
    public void registerFallbackProxy(final String userId,
                                      final String instanceId,
                                      final String accessData,
                                      final String ip,
                                      final String port) {
        RetryingTransaction<Void> txn = new RetryingTransaction<Void>() {
            protected Void run(Objectify ofy) {
                LanternInstance instance = findInstance(ofy,
                                                        userId,
                                                        instanceId);
                if (instance == null) {
                    // This may happen if we receive the fallback-proxy-up SQS
                    // notification before the first Available presence from
                    // the proxy.  Seems unlikely but there's nothing logically
                    // preventing that.
                    log.warning("Setting location for new instance!");
                    instance = new LanternInstance(instanceId,
                                                   getUserKey(userId));
                }
                instance.setFallbackProxy(true);
                instance.setAccessData(accessData);
                instance.setListenHostAndPort(ip + ":" + port);
                instance.setUser(userId);
                ofy.put(instance);

                LanternUser user = ofy.find(LanternUser.class, userId);
                user.setFallbackProxyUserId(userId);
                String old = user.getFallbackForNewInvitees();
                // We don't set it unconditionally because a user may have
                // more than one proxy running and nothing guarantees that
                // this is the one to which we're currently directing new
                // invitees.
                //
                // For example, if installer wrappers are rebuilt in the
                // fallback proxies, they will report themselves as running
                // again, in whatever order.
                if (StringUtils.isBlank(old)
                    || old.equals(
                        LanternControllerConstants.FALLBACK_PROXY_LAUNCHING)) {
                    user.setFallbackForNewInvitees(instanceId);
                }
                ofy.put(user);

                ofy.getTxn().commit();
                return null;
            }
        };
        txn.run();
        if (txn.failed()) {
            throw new RuntimeException("Too much contention!");
        }
    }

    /**
     * Increment the number of invites assigned to the current fallback proxy for
     * this user.
     *
     * @return the new number of invites, or null if the current fallback proxy
     * is being launched.
     */
    public Integer incrementFallbackInvites(final String userId,
                                            int increment) {
        final int STAT_QUANTUM = 10;
        if (increment == 1) {
            // We're getting spammed by these and this whole system is
            // stochastic anyway, so let's do statistical increments.
            if (new Random().nextInt(STAT_QUANTUM) == 0) {
                increment = STAT_QUANTUM;
            } else {
                // XXX HACK: don't launch a new proxy.
                //
                // Actual figures will be returned next time we roll a zero.
                // Ten more or less invites assigned to a proxy don't matter,
                // esp. when we're overloaded.
                return 0;
            }
        }
        final int inc = increment;
        RetryingTransaction<Integer> txn = new RetryingTransaction<Integer>() {
            protected Integer run(Objectify ofy) {
                String instanceId = ofy.find(LanternUser.class, userId)
                                       .getFallbackForNewInvitees();
                if (LanternControllerConstants.FALLBACK_PROXY_LAUNCHING
                                              .equals(instanceId)) {
                    return null;
                }
                LanternInstance instance = findInstance(ofy,
                                                        userId,
                                                        instanceId);
                instance.incrementNumberOfInvitesForFallback(inc);
                ofy.put(instance);
                ofy.getTxn().commit();
                return instance.getNumberOfInvitesForFallback();
            }
        };
        Integer ret = txn.run();
        if (txn.failed()) {
            log.severe("Too much contention!");
            if (increment > STAT_QUANTUM) {
                // We're handling a proxy-up notification.  We don't want to
                // hold the sending of invite emails to users that are waiting
                // for this proxy to come up, so the best we can do without
                // a bigger revamp of this system is to log this and at least
                // return the right value.
                logPermanently(UUID.randomUUID().toString(),
                               "Failed to record " + increment
                               + " invites for " + userId);
                return increment;
            }
            return 0;
        }
        return ret;
    }

    /**
     * Move installerLocation from user to fallbackProxy if necessary.
     *
     * TRANSITION: In the old scheme installerLocation was set by user.  This
     * code is for porting proxies that predate the fallback-balancing scheme.
     */
    public void transitionInstallerLocation(final String userId,
                                            final String instanceId) {
        RetryingTransaction<Void> txn = new RetryingTransaction<Void>() {
            protected Void run(Objectify ofy) {
                LanternUser user = ofy.find(LanternUser.class, userId);
                String insloc = user.getInstallerLocation();
                if (insloc == null) {
                    return null;
                }
                log.info("Moving legacy installer location from " + userId
                         + " to fallback proxy " + instanceId);
                user.setInstallerLocation(null);
                ofy.put(user);
                LanternInstance instance = findInstance(ofy,
                                                        userId,
                                                        instanceId);
                instance.setInstallerLocation(insloc);
                ofy.put(instance);
                ofy.getTxn().commit();
                return null;
            }
        };
        txn.run();
        if (txn.failed()) {
            log.severe("Transaction failed!");
        }
    }

    public int incrementFallbackSerialNumber(final String userId) {
        RetryingTransaction<Integer> txn = new RetryingTransaction<Integer>() {
            protected Integer run(Objectify ofy) {
                LanternUser user = ofy.find(LanternUser.class, userId);
                int ret = user.incrementFallbackSerialNumber();
                ofy.put(user);
                ofy.getTxn().commit();
                return ret;
            }
        };
        int ret = txn.run();
        if (txn.failed()) {
            throw new RuntimeException("Transaction failed!");
        }
        log.info("Incremented " + userId + "'s fallbackSerialNumber to "
                 + ret);
        return ret;
    }

    /**
     * RemoteApi script.
     */
    private static final boolean MASS_AUTHORIZE_INVITES_ENABLED = false;
    public void authorizeQueuedInvites() {
        if (!MASS_AUTHORIZE_INVITES_ENABLED) {
            log.severe("Edit Dao.java and set MASS_AUTHORIZE_INVITES_ENABLED"
                       + " to true to trigger this");
        }
        Map<String, Boolean> cache = new HashMap<String, Boolean>();
        for (Invite invite : ofy().query(Invite.class)
                                  .filter("status", Invite.Status.queued)) {
            String inviteeId = invite.getInvitee();
            boolean signedIn;
            if (cache.containsKey(inviteeId)) {
                signedIn = cache.get(inviteeId);
            } else {
                LanternUser invitee = findUser(inviteeId);
                signedIn = invitee != null && invitee.isEverSignedIn();
                cache.put(inviteeId, signedIn);
            }
            if (signedIn) {
                log.info("Ignoring invite to already signed in user "
                         + inviteeId);
            } else {
                FallbackProxyLauncher.authorizeInvite(invite.getInviter(),
                                                      inviteeId);
            }
        }
        log.info("All done.");
    }
    
    /**
     * Run the given call with Objectify and return the result.
     * 
     * @param call
     * @return
     */
    public <T> T withObjectify(final DbCall<T> call) {
        return call.call(ofy());
    }

    /**
     * Execute the given call in a transaction and return the result;
     * 
     * @param call
     * @return the result of calling call
     * @throws TxFailure
     *             if the transaction fails
     */
    public <T> T withTransaction(final DbCall<T> call) throws TxFailure {
        RetryingTransaction<T> tx = new RetryingTransaction<T>() {
            protected T run(Objectify ofy) {
                T result = call.call(ofy);
                ofy.getTxn().commit();
                return result;
            }
        };
        T result = tx.run();
        if (tx.failed()) {
            throw new RuntimeException("Transaction failed!");
        }
        return result;
    }
 
    /**
     * Callback for execution within a transaction.
     * 
     * @param <T>
     */
    public interface DbCall<T> {
        T call(Objectify ofy);
    }
    
    public static class TxFailure extends RuntimeException {
        public TxFailure() {
            super("Transaction failed");
        }
    }

    public void setConfigFolder(final String userId,
                                final String configFolder) {
        log.info("Setting config folder for " + userId
                 + " to '" + configFolder.substring(0, 10) + "'...");
        RetryingTransaction<Void> t = new RetryingTransaction<Void>() {
            protected Void run(Objectify ofy) {
                LanternUser user = ofy.find(LanternUser.class, userId);
                user.setConfigFolder(configFolder);
                ofy.put(user);
                ofy.getTxn().commit();
                return null;
            }
        };
        t.run();
        if (t.failed()) {
            log.severe("Error trying to set config folder!");
        }
    }

    public void setWrappersUploaded(final String userId) {
        RetryingTransaction<Void> t = new RetryingTransaction<Void>() {
            protected Void run(Objectify ofy) {
                LanternUser user = ofy.find(LanternUser.class, userId);
                user.setWrappersUploaded();
                ofy.put(user);
                ofy.getTxn().commit();
                return null;
            }
        };
        t.run();
        if (t.failed()) {
            log.severe("Error trying to set wrappersUploaded!");
        }
    }

    public void sendInvitesTo(final String userId) {
        log.info("Sending all authorized invites to " + userId);
        LanternUser invitee = findUser(userId);
        if (invitee == null) {
            throw new RuntimeException("Not a LanternUser");
        }
        String configFolder = invitee.getConfigFolder();
        if (configFolder == null) {
            throw new RuntimeException("No config folder.");
        }
        for (Invite inv : ofy().query(Invite.class)
                               .filter("invitee", userId)
                               .filter("status", Invite.Status.authorized)) {
            log.info("Got an invite from " + inv.getInviter());
            if (!setInviteStatus(inv.getInviter(),
                                 userId,
                                 Invite.Status.authorized,
                                 Invite.Status.sending)) {
                log.warning("Weird race condition trying to advance invite?");
                continue;
            }
            LanternUser inviter = findUser(inv.getInviter());
            QueueFactory.getDefaultQueue().add(
                TaskOptions.Builder
                   .withUrl("/send_invite_task")
                   .param("inviterName", "" + inviter.getName()) // handle null
                   .param("inviterEmail", inviter.getId())
                   .param("inviteeEmail", userId)
                   .param("configFolder", configFolder)
                   .param("template", inv.getEmailTemplate()));
            log.info("Successfully enqueued invite email.");
        }
    }

    public void retrofitConfigAndWrappers(final String userId) {
        RetryingTransaction<Void> t = new RetryingTransaction<Void>() {
            protected Void run(Objectify ofy) {
                LanternUser user = ofy.find(LanternUser.class, userId);
                if (user.getConfigFolder() == null) {
                    // Not bothering with a constant because any non-null value
                    // will do.
                    user.setConfigFolder("pending");
                    enqueueConfigAndWrappers(userId);
                    ofy.put(user);
                    ofy.getTxn().commit();
                }
                return null;
            }
        };
        t.run();
        if (t.failed()) {
            log.severe("Transaction failed!");
        }
    }

    private void enqueueConfigAndWrappers(String userId) {
        QueueFactory.getDefaultQueue().add(
                TaskOptions.Builder
                .withUrl("/upload_config_and_request_wrappers")
                .param("userId", userId));
    }

    /**
     * Add a friend entry, bypassing quota checks, unless there's one already.
     */
    public void addFriend(final String whose, final String friend) {
        RetryingTransaction<Void> t = new RetryingTransaction<Void>() {
            protected Void run(Objectify ofy) {
                Key<FriendingQuota> ancestorKey = Key.create(FriendingQuota.class, whose);
                Key<LanternFriend> key = Key.create(ancestorKey, LanternFriend.class, friend);
                if (ofy.find(key) != null) {
                    log.info("Friend already exists.");
                    return null;
                }
                LanternFriend lf = new LanternFriend(friend);
                lf.setUserEmail(whose);
                lf.setStatus(Friend.Status.friend);
                ofy.put(lf);
                ofy.getTxn().commit();
                return null;
            }
        };
        t.run();
        if (t.failed()) {
            log.severe("Transaction failed!");
        }
    }

    public void moveToNewFallback(final String userId,
                                  final String fallbackProxyUserId,
                                  final String fallbackInstanceId) {
        RetryingTransaction<Void> t = new RetryingTransaction<Void>() {
            protected Void run(Objectify ofy) {
                LanternUser user = ofy.find(LanternUser.class, userId);
                if (user == null) {
                    log.severe("User not found: " + userId);
                    return null;
                }
                log.info("Moving user " + userId
                        + " to fallback " + fallbackInstanceId);
                user.setFallbackProxyUserId(fallbackProxyUserId);
                user.setFallbackProxy(getInstanceKey(fallbackProxyUserId,
                                                     fallbackInstanceId));
                ofy.put(user);
                ofy.getTxn().commit();
                return null;
            }
        };
        t.run();
        if (t.failed()) {
            log.severe("Transaction failed!");
        } else {
            S3Config.refreshConfig(userId);
        }
    }
}
