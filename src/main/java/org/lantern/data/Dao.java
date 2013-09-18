package org.lantern.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.lantern.CensoredUtils;
import org.lantern.InvitedServerLauncher;
import org.lantern.JsonUtils;
import org.lantern.LanternControllerConstants;
import org.lantern.MandrillEmailer;
import org.lantern.Stats;
import org.lantern.admin.PendingInvites;
import org.lantern.data.Invite.Status;
import org.lantern.state.Friend;
import org.lantern.state.Friends;
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

public class Dao extends DAOBase {

    private final transient Logger log = Logger.getLogger(getClass().getName());

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
        ObjectifyService.register(InstallerBucket.class);
        ObjectifyService.register(PermanentLogEntry.class);
        ObjectifyService.register(TrustRelationship.class);

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

    public Collection<LanternUser> getAllUsers() {
        final Objectify ofy = ObjectifyService.begin();
        final Query<LanternUser> users = ofy.query(LanternUser.class);
        return users.list();
    }

    public void setInstanceAvailable(final String userId,
            final String instanceId, final String countryCode, final Mode mode,
            final boolean isFallback, final String resource) {

        Boolean result = new RetryingTransaction<Boolean>() {
            @Override
            protected Boolean run(Objectify ofy) {
                final List<String> countersToUpdate = setInstanceAvailable(
                        ofy, userId, instanceId, countryCode, mode, isFallback,
                        resource);
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
     * @return a list of the counters that should be incremented as a
     * result of this instance having become available.
     */
    public List<String> setInstanceAvailable(Objectify ofy,
            String userId, final String instanceId, final String countryCode,
            final Mode mode, final boolean isFallback, final String resource) {

        String modeStr = mode.toString();
        LanternUser user = ofy.find(LanternUser.class, userId);

        Key<LanternUser> parentKey = new Key<LanternUser>(LanternUser.class,
                userId);

        Key<LanternInstance> key = new Key<LanternInstance>(parentKey,
                LanternInstance.class, instanceId);

        LanternInstance instance = ofy.find(key);
        
        if (instance != null && StringUtils.equals(instance.getResource(), resource)) {
            //this is an available message for the same resource as
            //is currently in use, so it must be bogus.
            return Collections.emptyList();
        }

        ArrayList<String> counters = new ArrayList<String>();
        if (instance != null) {
            instance.setFallback(isFallback);

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
            instance.setLastUpdated(new Date());
            instance.setResource(resource);
            ofy.put(instance);
        } else {
            log.info("Could not find instance!!");

            instance = new LanternInstance(instanceId, parentKey);
            instance.setResource(resource);
            instance.setUser(userId);
            // The only counter that we need handling differently for new
            // instances is the global peers ever.
            counters.add(dottedPath(GLOBAL, NPEERS, EVER, modeStr));
            updateStatsForNewlyAvailableInstance(ofy, user, instance,
                    countryCode, mode, counters);
        }
        return counters;
    }
    
    /**
     * Update the 
     * @param userId
     * @param instanceId
     * @param stats
     */
    public void updateInstanceStats(final String userId,
            final String instanceId, final Stats stats) {
        final long now = System.currentTimeMillis();
        RetryingTransaction<Void> tx = new RetryingTransaction<Void>() {
            @Override
            public Void run(Objectify ofy) {
                LanternInstance instance = findLanternInstance(ofy, userId, instanceId);
                instance.getProcessCpuUsage().addSample(now,
                        stats.getProcessCpuUsage());
                instance.getSystemCpuUsage().addSample(now,
                        stats.getSystemCpuUsage());
                instance.getSystemLoadAverage().addSample(now,
                        stats.getSystemLoadAverage());
                instance.getMemoryUsageInBytes().addSample(now,
                        stats.getMemoryUsageInBytes());
                instance.getNumberOfOpenFileDescriptors().addSample(now,
                        stats.getNumberOfOpenFileDescriptors());
                ofy.put(instance);
                ofy.getTxn().commit();
                log.info("Successfully updated stats for LanternInstance");
                return null;
            }
        };
        tx.run();
        
        if (tx.failed()) {
            log.warning(String.format(
                    "Unable to record stats for user: %1$s instance: %2$s",
                    userId, instanceId));
        }
    }

    /**
     * Possibly modifies the user, instance and the counters (via the
     * `counters` list).
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
        instance.setLastUpdated(new Date());
        ofy.put(instance);
        ofy.put(user);
        log.info("Finished updating datastore...");
    }

    private static String dottedPath(String ... strings) {
        return StringUtils.join(strings, ".");
    }

    public int getInvites(final String userId) {
        final Objectify ofy = ofy();
        final LanternUser user = ofy.find(LanternUser.class, userId);
        if (user == null) {
            return 0;
        }
        return user.getInvites();
    }


    public boolean hasMoreInvites(final String userId) {
        return getInvites(userId) > 0;
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
     *
     * @param sponsor
     * @param inviteeEmail
     * @return
     */
    public boolean addInvite(final String sponsor, final String inviteeEmail,
            final String refreshToken) {
        // We just add the invite object here. We create the invitee when
        // the invite is sent in sendingInvite

        Boolean result = new RetryingTransaction<Boolean>() {
            @Override
            protected Boolean run(Objectify ofy) {
                LanternUser inviter = ofy.find(LanternUser.class, sponsor);
                if (inviter == null) {
                    log.warning("Could not find sponsor sending invite: " + sponsor);
                    return false;
                }

                if (alreadyInvitedBy(ofy, sponsor, inviteeEmail)) {
                    log.info("Not re-sending e-mail since user is already invited");
                    return false;
                }

                inviter.setRefreshToken(refreshToken);
                ofy.put(inviter);

                Invite invite = new Invite(sponsor, inviteeEmail);
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

        return true;
    }

    /**
     * Uncomment the body of this method (and the call in RemoteApi.java) when
     * initializing lantern-controller
     */
    public void createInitialUser(final String email) {
//        final Objectify ofy = ofy(); final LanternUser user = new
//        LanternUser(email); user.setDegree(1); user.setEverSignedIn(true);
//        user.setInvites(5); user.setSponsor("adamfisk@gmail.com");
//        ofy.put(user); log.info("Finished adding invite...");

    }

    public boolean sendingInvite(final String inviterEmail,
            final String inviteeEmail, final boolean noCost) {

        boolean status = new RetryingTransaction<Boolean>() {
            @Override
            public Boolean run(Objectify ofy) {
                final Invite invite = getInvite(ofy, inviterEmail, inviteeEmail);
                long now = System.currentTimeMillis();

                if (invite.getStatus() == Status.sent) {
                    return false;
                } else if (invite.getStatus() == Status.sending) {
                    // we have already decremented the invite count, so we
                    // don't need to do that. But we might need to send the
                    // email again if the previous attempt crashed during
                    // email sending.

                    // we will only attempt to send an email once every minute,
                    // so that concurrent attempts don't send multiple emails.
                    if (now - invite.getLastAttempt() > 60 * 1000) {
                        invite.setLastAttempt(now);
                        ofy.put(invite);
                        ofy.getTxn().commit();
                        return true;
                    } else {
                        return false;
                    }
                }
                // invite status is queued, so we have never tried sending an
                // invite

                invite.setStatus(Status.sending);
                invite.setLastAttempt(now);
                ofy.put(invite);

                final LanternUser inviter = ofy.find(LanternUser.class,
                        inviterEmail);
                if (inviter == null) {
                    log.severe("Finalizing invites of nonexistent user?");
                    return false;
                }
                if (!noCost) {
                    final int curInvites = inviter.getInvites();
                    if (curInvites < 1) {
                        if (inviter.getDegree() != 0) {
                            log.info("Decrementing invites on non-admin user with no invites");
                            return false;
                        }
                        // inviter is admin with no invites
                    } else {
                        log.info("Decrementing invites for " + inviterEmail);
                        inviter.setInvites(curInvites - 1);
                        ofy.put(inviter);
                    }
                }
                ofy.getTxn().commit();
                log.info("Transaction successful.");
                return true;
            }
        }.run();

        createInviteeUser(inviterEmail, inviteeEmail);
        return status;
    }

    private boolean createInviteeUser(final String inviterEmail,
            final String inviteeEmail) {

        // get the inviter outside the loop because we don't care
        // about concurrent modifications
        final Objectify ofy = ofy();
        final LanternUser inviter = ofy.find(LanternUser.class, inviterEmail);

        Boolean result = new RetryingTransaction<Boolean>() {
            @Override
            public Boolean run(Objectify ofy) {

                LanternUser invitee = ofy.find(LanternUser.class, inviteeEmail);
                if (invitee == null) {
                    log.info("Adding invitee to database");
                    invitee = new LanternUser(inviteeEmail);

                    invitee.setDegree(inviter.getDegree() + 1);
                    if (getUserCount() < LanternControllerConstants.MAX_USERS
                            && invitee.getInvites() < 2) {
                        invitee.setInvites(getDefaultInvites());
                    }
                    invitee.setSponsor(inviter.getId());
                    ofy.put(invitee);
                } else {
                    log.info("Invitee exists, nothing to do here.");
                    return true;
                }
                ofy.getTxn().commit();
                log.info("Successfully committed attempt to add invitee.");
                return true;
            }
        }.run();

        if (result == null) {
            return false;
        } else {
            return result;
        }
    }

    public boolean alreadyInvitedBy(Objectify ofy, final String inviterEmail,
            final String inviteeEmail) {
        final Invite invite = getInvite(ofy, inviterEmail, inviteeEmail);
        return invite != null;
    }

    private Invite getInvite(Objectify ofy, final String inviterEmail,
            final String inviteeEmail) {
        Key<LanternUser> parentKey = new Key<LanternUser>(LanternUser.class,
                inviterEmail);
        String id = Invite.makeId(inviterEmail, inviteeEmail);
        final Invite invite = ofy.find(new Key<Invite>(parentKey, Invite.class,
                id));
        return invite;
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

    public String getAndSetInstallerLocation(final String email) {
        RetryingTransaction<String> txn = new RetryingTransaction<String>() {
            @Override
            public String run(Objectify ofy) {
                LanternUser user = ofy.find(LanternUser.class, email);
                String old = user.getInstallerLocation();
                if (old == null) {
                    user.setInstallerLocation(InvitedServerLauncher.PENDING);
                    ofy.put(user);
                }
                ofy.getTxn().commit();
                return old;
            }
        };

        String old = txn.run();
        if (txn.failed()) {
            // XXX: is really returning our best guess better than failing?
            log.warning("Gave up because of too much contention!");
        }
        return old;
    }

   /** Perform, as an atomic operation, the setting of the installerLocation
     *  and the getting of whatever invitees had been waiting for the installers
     *  to be built.
     *
     *  This is to prevent the case that an invitation is processed between
     *  these operations, thus triggering the sending of two invite e-mails
     *  (one in the handling of the server-up event, and another in the
     *  handling of the invitation proper, which sees the server as available
     *  and thus sends the e-mail immediately).
     *
     *  While this would not be terrible, it's not hard to avoid either.
     */
    public Collection<String> setInstallerLocationAndGetInvitees(
            final String inviterEmail, final String installerLocation)
            throws UnknownUserException {
        final Collection<String> results = new HashSet<String>();
        // The GAE datastore only gives strong consistency guarantees for
        // queries that specify an 'ancestor' constraint ("ancestor queries").
        // In addition, no other queries are allowed in a transaction.
        //
        // As of this writing, we are only ever querying invites per inviter,
        // hence the grouping.
        final Key<LanternUser> ancestor = new Key<LanternUser>(
                LanternUser.class, inviterEmail);
        Boolean result = new RetryingTransaction<Boolean>() {
            @Override
            public Boolean run(Objectify ofy) {
                // We don't need to reset `results` inside the loop because it
                // will
                // only ever grow. If we get a collision, the only point of
                // retrying is to incorporate any new invites.

                LanternUser user = ofy.find(LanternUser.class, inviterEmail);
                if (user == null) {
                    throw new UnknownUserException(inviterEmail);
                }
                user.setInstallerLocation(installerLocation);
                ofy.put(user);
                final Query<Invite> invites = ofy.query(Invite.class).ancestor(
                        ancestor);
                for (Invite invite : invites) {
                    results.add(invite.getInvitee());
                }

                ofy.getTxn().commit();
                log.info("Returning instances: " + results);
                return true;
            }
        }.run();

        if (result == null) {
            log.warning("Gave up because of too many failed transactions!");
        }
        // Since the correctness of this is not critical, returning our best
        // effort guess is better than failing altogether.
        return results;
    }

    public void addInstallerBucket(final String name) {
        // We don't wrap this in a transaction because we add buckets serially
        // as a preprocess.  Also, a race condition would only overwrite
        // a bucket that we have just added, which would have the same count.
        final Objectify ofy = ofy();
        final InstallerBucket bucket = ofy.find(InstallerBucket.class, name);
        if (bucket != null) {
            log.severe("Tried to add existing bucket?");
            return;
        }
        ofy.put(new InstallerBucket(name));
    }

    public String getAndIncrementLeastUsedBucket() {
        // Not really guaranteed to return the minimum, but since we'll make
        // sure to increment whatever we get, we get a balanced bucket usage
        // eventually.
        final InstallerBucket leastUsed = ofy().query(InstallerBucket.class)
                .order("installerLocations").get();
        Boolean result = new RetryingTransaction<Boolean>() {
            @Override
            public Boolean run(Objectify ofy) {
                leastUsed.setInstallerLocations(leastUsed
                        .getInstallerLocations() + 1);
                ofy.put(leastUsed);
                ofy.getTxn().commit();
                return true;
            }
        }.run();
        if (result == null) {
            // If we ever get this we probably want to move to sharded counters
            // instead.
            log.warning("Too much contention for buckets; you may want to look into this.");
        }
        return leastUsed.getId();
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

    public void logPermanently(final String contents) {
        ofy().put(new PermanentLogEntry(contents));
        log.info("Logged!");
    }

    /** Add n invites to each user.  N may be negative; a user who would have
     *  negative invites as a result of this instead gets zero invites
     *
     * @param n
     */
    public void globalAddInvites(int n) {
        Objectify ofy = ofy();
        for (LanternUser user : ofy.query(LanternUser.class)) {
            user.setInvites(Math.max(user.getInvites() + n, 0));
            ofy.put(user);
        }
    }
    public boolean areInvitesPaused() {
        return settingsManager.getBoolean("invitesPaused");
    }

    public void setInvitesPaused(boolean paused) {
        settingsManager.set("invitesPaused", "" + paused);
    }

    public void setDefaultInvites(int n) {
        settingsManager.set("defaultInvites", "" + n);
    }

    public int getDefaultInvites() {
        try {
            return Integer.parseInt(settingsManager.get("defaultInvites"));
        } catch (NumberFormatException e) {
            return 2;
        }
    }

    public boolean isAdmin(String userId) {
        Objectify ofy = ofy();
        LanternUser user = ofy.find(LanternUser.class, userId);
        return user.getDegree() == 0;
    }

    public List<Friend> syncFriends(final String userId,
            final Friends clientFriends) {
        List<Friend> result = new RetryingTransaction<List<Friend>>() {
            @Override
            public List<Friend> run(Objectify ofy) {
                List<Friend> updated = new ArrayList<Friend>();
                Key<LanternUser> parentKey = new Key<LanternUser>(LanternUser.class,
                        userId);
                Collection<Friend> clientFriendList = clientFriends.getFriends();

                Query<TrustRelationship> relationships = ofy.query(TrustRelationship.class).ancestor(
                        parentKey);
                Map<String, TrustRelationship> relationshipSet = new HashMap<String, TrustRelationship>();
                for (TrustRelationship relationship : relationships) {
                    relationshipSet.put(relationship.getId(), relationship);
                }
                boolean save = false;
                for (Friend friend : clientFriendList) {
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
                    Friend friend = new org.lantern.data.ServerFriend(relationship.getId());
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

    public void syncFriend(final String userId, final Friend clientFriend) {
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

    public void sentInvite(final String inviterEmail, final String invitedEmail) {
        Boolean inviteFinalized = new RetryingTransaction<Boolean>() {
            @Override
            protected Boolean run(Objectify ofy) {
                Invite invite = getInvite(ofy, inviterEmail, invitedEmail);
                invite.setStatus(Status.sent);
                ofy.put(invite);
                ofy.getTxn().commit();
                return true;
            }

        }.run();

        if (inviteFinalized == null)
            inviteFinalized = false;
        String status = inviteFinalized ? "" : "not ";
        log.info("Invite " + status + "finalized: " + inviterEmail + " to "
                + invitedEmail);
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

    public LanternUser getUser(String email) {
        MemcacheService cache = MemcacheServiceFactory.getMemcacheService();
        LanternUser user = (LanternUser) cache.get("user " + email);
        if (user == null) {
            try {
                Objectify ofy = ofy();
                user = ofy.get(LanternUser.class, email);
                cache.put("user" + email, user, Expiration.byDeltaSeconds(1000));
            } catch (NotFoundException e) {
                return null;
            }
        }
        return user;
    }
    
    private LanternInstance findLanternInstance(
            Objectify ofy,
            String userId,
            String instanceId) {
        Key<LanternUser> parentKey = new Key<LanternUser>(LanternUser.class,
                userId);

        Key<LanternInstance> key = new Key<LanternInstance>(parentKey,
                LanternInstance.class, instanceId);
        
        return ofy.find(key);
    }
}
