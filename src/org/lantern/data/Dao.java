package org.lantern.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
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
import org.lantern.LanternControllerUtils;
import org.lantern.data.LanternUser.SyncResult;
import org.lantern.state.Friend;
import org.lantern.state.Friends;
import org.lantern.state.Mode;

import com.googlecode.objectify.Key;
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

    private static final int TXN_RETRIES = 10;

    static {
        ObjectifyService.register(LanternUser.class);
        ObjectifyService.register(LanternInstance.class);
        ObjectifyService.register(Invite.class);
        ObjectifyService.register(InstallerBucket.class);
        ObjectifyService.register(PermanentLogEntry.class);

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

    public void setInstanceAvailable(String userId, final String instanceId,
            final String countryCode, final Mode mode) {
        for (int retries=TXN_RETRIES; retries>0; retries--) {
            final Objectify ofy = ObjectifyService.beginTransaction();
            try {
                final ArrayList<String> countersToUpdate
                    = setInstanceAvailable(
                        ofy, userId, instanceId, countryCode, mode);
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
                return;
            } catch (final ConcurrentModificationException e) {
                // When a user logs in we get duplicated presence events for
                // some reason.  This is why we put this method in
                // a transaction, but we wouldn't really want to retry on this
                // condition.  We do retry because this transaction can also
                // fail if someone concurrently modifies the LanternUser that
                // owns this instance.
                log.info("Concurrent modification! Retrying...");
                continue;
            } finally {
                if (ofy.getTxn().isActive()) {
                    ofy.getTxn().rollback();
                }
            }
        }
        log.warning("Too much contention; giving up!");
    }

    /**
     * @return a list of the counters that should be incremented as a
     * result of this instance having become available.
     */
    public ArrayList<String> setInstanceAvailable(Objectify ofy,
            String userId, final String instanceId, final String countryCode,
            final Mode mode) {
        // As of this writing, we use instanceId to refer to the XMPP
        // resource, that being the instance-specific part of the jabberId.
        // Note that this does *not* identify an instance globally.  You need
        // the userId too.  That is why, somewhat confusingly, instances are
        // keyed by full jabberId in the LanternInstances table.
        final String
            fullId = LanternControllerUtils.jabberIdFromUserAndResource(
                        userId, instanceId);
        LanternInstance instance = ofy.find(LanternInstance.class, fullId);
        String modeStr = mode.toString();
        LanternUser user = ofy.find(LanternUser.class, userId);
        ArrayList<String> counters = new ArrayList<String>();
        if (instance != null) {
            log.info("Setting availability to true for " + fullId);
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
                    ofy.put(instance);
                    log.info("Finished updating datastore...");
                }

            } else {
                // XXX will we ever see this anyway?
                log.info("Instance exists but was unavailable.");
                updateStatsForNewlyAvailableInstance(ofy, user, instance,
                        countryCode, mode, counters);
            }

        } else {
            log.info("Could not find instance!!");
            instance = new LanternInstance(fullId);
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
        if (!user.anyInstancesSignedIn()) {
            counters.add(dottedPath(GLOBAL, NUSERS, ONLINE));
            counters.add(dottedPath(countryCode, NUSERS, ONLINE));
        }
        user.incrementInstancesSignedIn();
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
     * Returns true if the invite was added, false if it wasn't (because it
     * already existed, for instance)
     *
     * @param sponsor
     * @param email
     * @return
     */
    public boolean addInvite(final String sponsor, final String email) {
        // We do two different transactions here: one to add the invite if not
        // there and, if this succeeds, another one to add the invitee if not
        // there.

        int retries;
        Objectify ofy;

        // Initialized only to appease the compiler.
        LanternUser inviter = new LanternUser("bogus@never.used");

        for (retries=TXN_RETRIES; retries>0; retries--) {
            ofy = ObjectifyService.beginTransaction();
            try {
                inviter = ofy.find(LanternUser.class, sponsor);
                if (inviter == null) {
                    log.warning("Could not find sponsor sending invite: " + sponsor);
                    return false;
                }

                if (alreadyInvitedBy(ofy, sponsor, email)) {
                    log.info("Not re-sending e-mail since user is already invited");
                    return false;
                }

                Invite invite = new Invite(sponsor, email);
                ofy.put(invite);

                ofy.getTxn().commit();
                log.info("Successfully committed new invite.");
                break;
            } catch (ConcurrentModificationException e) {
                log.warning("Concurrent modification trying to add invite.");
                continue;
            } finally {
                if (ofy.getTxn().isActive()) {
                    ofy.getTxn().rollback();
                }
            }
        }

        if (retries == 0) {
            log.warning("Too much contention trying to add invite; gave up.");
            return false;
        }

        for (retries=TXN_RETRIES; retries>0; retries--) {
            ofy = ObjectifyService.beginTransaction();
            try {
                LanternUser invitee = ofy.find(LanternUser.class, email);
                if (invitee == null) {
                    log.info("Adding invitee to database");
                    invitee = new LanternUser(email);

                    invitee.setDegree(inviter.getDegree() + 1);
                    if (getUserCount() < LanternControllerConstants.MAX_USERS
                        && invitee.getInvites() < 2) {
                        invitee.setInvites(getDefaultInvites());
                    }
                    invitee.setSponsor(sponsor);
                    ofy.put(invitee);
                } else {
                    log.info("Invitee exists, nothing to do here.");
                    return true;
                }
                ofy.getTxn().commit();
                log.info("Successfully committed attempt to add invitee.");
                return true;
            } catch (Exception e) {
                log.warning("Concurrent modification trying to add invite*e*.");
                continue;
            } finally {
                if (ofy.getTxn().isActive()) {
                    ofy.getTxn().rollback();
                }
            }
        }
        // I don't think we should ever see this.
        log.warning("Contention for a user we're trying to create?");
        // Anyway, the invite was added, so...
        return true;
    }

    /**
     * Uncomment the body of this method (and the call in RemoteApi.java) when
     * initializing lantern-controller
     */
    public void createInitialUser(final String email) {
        /*
        final Objectify ofy = ofy();
        final LanternUser user = new LanternUser(email);
        user.setDegree(1);
        user.setEverSignedIn(true);
        user.setInvites(5);
        user.setSponsor("adamfisk@gmail.com");
        ofy.put(user);
        log.info("Finished adding invite...");
        */

    }

    public void decrementInvites(final String userId) {
        log.info("Decrementing invites for "+userId);
        for (int retries=TXN_RETRIES; retries>0; retries--) {
            final Objectify ofy = ObjectifyService.beginTransaction();
            try {
                final LanternUser user = ofy.find(LanternUser.class, userId);
                if (user == null) {
                    log.severe("Decrementing invites of uninvited user?");
                    return;
                }
                final int curInvites = user.getInvites();
                final int newInvites;
                if (curInvites < 1) {
                    log.severe("Decrementing invites on user with no invites");
                    newInvites = 0;
                } else {
                    newInvites = curInvites - 1;
                }
                user.setInvites(newInvites);
                ofy.put(user);
                ofy.getTxn().commit();
                log.info("Transaction successful.");
                return;
            } catch (final ConcurrentModificationException e) {
                log.info("Concurrent modification! Retrying...");
                continue;
            } finally {
                if (ofy.getTxn().isActive()) {
                    ofy.getTxn().rollback();
                }
            }
        }
        log.warning("Too much contention; giving up!");
    }

    public boolean alreadyInvitedBy(Objectify ofy, final String inviterEmail,
        final String inviteeEmail) {
        Key<LanternUser> parentKey = new Key<LanternUser>(
                LanternUser.class, inviterEmail);
        String id = Invite.makeId(inviterEmail, inviteeEmail);
        final Invite invite = ofy.find(new Key<Invite>(
                parentKey, Invite.class, id));
        return invite != null;
    }

    public boolean isInvited(final String email) {
        final Objectify ofy = ofy();
        final LanternUser user = ofy.find(LanternUser.class, email);
        return user != null;
    }

    public void updateLastAccessed(final String email) {
        for (int retries=TXN_RETRIES; retries>0; retries--) {
            Objectify ofy = ObjectifyService.beginTransaction();
            try {
                final LanternUser user = ofy.find(LanternUser.class, email);
                if (user != null) {
                    user.setLastAccessed(new Date());
                    ofy.put(user);
                }
                ofy.getTxn().commit();
                return;
            } catch (ConcurrentModificationException e) {
                log.warning("Concurrent modification.");
                continue;
            } finally {
                if (ofy.getTxn().isActive()) {
                    ofy.getTxn().rollback();
                }
            }
        }
        log.warning("Too much contention!");
    }

    public boolean updateUser(final String userId, final long directRequests,
        final long directBytes, final long requestsProxied,
        final long bytesProxied, final String countryCode,
        final String instanceId, final Mode mode) {
        log.info(
            "Updating user with stats: dr: "+directRequests+" db: "+
            directBytes+" bytesProxied: "+bytesProxied);
        for (int retries=TXN_RETRIES; retries>0; retries--) {
            final Objectify ofy = ObjectifyService.beginTransaction();
            try {
                LanternUser user = ofy.find(LanternUser.class, userId);
                boolean isUserNew = (user == null);
                if (isUserNew) {
                    log.info("Could not find user!!");
                    user = new LanternUser(userId);
                }
                user.setBytesProxied(user.getBytesProxied() + bytesProxied);
                user.setRequestsProxied(user.getRequestsProxied() + requestsProxied);
                user.setDirectBytes(user.getDirectBytes() + directBytes);
                user.setDirectRequests(user.getDirectRequests() + directRequests);

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
            } catch (final ConcurrentModificationException e) {
                log.info("Concurrent modification! Retrying...");
                continue;
            } finally {
                if (ofy.getTxn().isActive()) {
                    ofy.getTxn().rollback();
                }
            }
        }
        // The caller expects some return value.
        throw new RuntimeException("Too much contention!");
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

    public void signedIn(final String email) {
        for (int retries=TXN_RETRIES; retries > 0; retries--) {
            Objectify ofy = ObjectifyService.beginTransaction();
            try {
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
                }
                return;
            } catch (final ConcurrentModificationException e) {
                log.info("Concurrent modification! Retrying transaction...");
                continue;
            } finally {
                if (ofy.getTxn().isActive()) {
                    ofy.getTxn().rollback();
                }
            }
        }
    }

    public void setInstanceUnavailable(String userId, final String instanceId) {
        for (int retries=TXN_RETRIES; retries>0; retries--) {
            final Objectify ofy = ObjectifyService.beginTransaction();
            try {
                final ArrayList<String> countersToUpdate
                    = setInstanceUnavailable(
                        ofy, userId, instanceId);
                ofy.getTxn().commit();
                // We only actually update the counters when we know the
                // transaction succeeded.  Since these affect the memcache
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
                return;
            } catch (final ConcurrentModificationException e) {
                // When a user logs in we get duplicated presence events for
                // some reason.  This is why we put this method in
                // a transaction, but we wouldn't really want to retry on this
                // condition.  We do retry because this transaction can also
                // fail if someone concurrently modifies the LanternUser that
                // owns this instance.
                log.info("Concurrent modification! Retrying...");
                continue;
            } finally {
                if (ofy.getTxn().isActive()) {
                    ofy.getTxn().rollback();
                }
            }
        }
        log.warning("Too much contention; giving up!");
    }
    public ArrayList<String> setInstanceUnavailable(Objectify ofy, String userId, String instanceId) {
        // As of this writing, we use instanceId to refer to the XMPP
        // resource, that being the instance-specific part of the jabberId.
        // Note that this does *not* identify an instance globally.  You need
        // the userId too.  That is why, somewhat confusingly, instances are
        // keyed by full jabberId in the LanternInstances table.
        ArrayList<String> counters = new ArrayList<String>();
        final String
            fullId = LanternControllerUtils.jabberIdFromUserAndResource(
                        userId, instanceId);
        final LanternInstance instance = ofy.find(LanternInstance.class, fullId);
        if (instance == null) {
            log.warning("Instance " + fullId + " not found.");
            return counters;
        }
        if (instance.isAvailable()) {
            log.info("Decrementing online count");
            instance.setAvailable(false);
            LanternUser user = ofy.find(LanternUser.class, userId);
            user.decrementInstancesSignedIn();

            String modeStr = instance.getMode().toString();
            String countryCode = instance.getCurrentCountry();

            counters.add("-" + dottedPath(GLOBAL, NPEERS, ONLINE, modeStr));
            counters.add("-" + dottedPath(countryCode, NPEERS, ONLINE, modeStr));

            if (!user.anyInstancesSignedIn()) {
                log.info("Decrementing online user count");
                counters.add("-" + dottedPath(GLOBAL, NUSERS, ONLINE));
                counters.add("-" + dottedPath(countryCode, NUSERS, ONLINE));
            }

            ofy.put(instance);
            ofy.put(user);
        }
        return counters;
    }

    public String getAndSetInstallerLocation(final String email) {
    	String old = null;
    	for (int retries=TXN_RETRIES; retries > 0; retries--) {
            Objectify ofy = ObjectifyService.beginTransaction();
    		try {
                LanternUser user = ofy.find(LanternUser.class, email);
                old = user.getInstallerLocation();
                if (old == null) {
                    user.setInstallerLocation(InvitedServerLauncher.PENDING);
                    ofy.put(user);
                }
                ofy.getTxn().commit();
                return old;
    		} catch (final ConcurrentModificationException e) {
                log.info("Concurrent modification! Retrying transaction...");
    			continue;
    		} finally {
                if (ofy.getTxn().isActive()) {
                    ofy.getTxn().rollback();
                }
    		}
    	}
    	//XXX: is really returning our best guess better than failing?
    	log.warning("Gave up because of too much contention!");
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
        Collection<String> results = new HashSet<String>();
        // The GAE datastore only gives strong consistency guarantees for
        // queries that specify an 'ancestor' constraint ("ancestor queries").
        // In addition, no other queries are allowed in a transaction.
        //
        // As of this writing, we are only ever querying invites per inviter,
        // hence the grouping.
        final Key<LanternUser>
            ancestor = new Key<LanternUser>(LanternUser.class, inviterEmail);
        for (int retries=TXN_RETRIES; retries > 0; retries--) {
            // We don't need to reset `results` inside the loop because it will
            // only ever grow.  If we get a collision, the only point of
            // retrying is to incorporate any new invites.
            Objectify txnOfy = ObjectifyService.beginTransaction();
            try {
                LanternUser user = txnOfy.find(LanternUser.class,
                                               inviterEmail);
                if (user == null) {
                    throw new UnknownUserException(inviterEmail);
                }
                user.setInstallerLocation(installerLocation);
                txnOfy.put(user);
                final Query<Invite> invites = txnOfy.query(Invite.class)
                                                 .ancestor(ancestor);
                for (Invite invite : invites) {
                    results.add(invite.getInvitee());
                }

                txnOfy.getTxn().commit();
                log.info("Returning instances: "+results);
                return results;
            } catch (final ConcurrentModificationException e) {
                log.info("Concurrent modification! Retrying transaction...");
                continue;
            } finally {
                if (txnOfy.getTxn().isActive()) {
                    txnOfy.getTxn().rollback();
                }
            }
        }
        log.warning("Gave up because of too many failed transactions!");
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
    	for (int retries=TXN_RETRIES; retries > 0; retries--) {
            Objectify txnOfy = ObjectifyService.beginTransaction();
            try {
                leastUsed.setInstallerLocations(
                        leastUsed.getInstallerLocations() + 1);
                txnOfy.put(leastUsed);
                txnOfy.getTxn().commit();
                return leastUsed.getId();
            } catch (final ConcurrentModificationException e) {
                log.info("Concurrent modification! Retrying transaction...");
    			continue;
    		} finally {
                if (txnOfy.getTxn().isActive()) {
                    txnOfy.getTxn().rollback();
                }
    		}
        }
        // If we ever get this we probably want to move to sharded counters instead.
        log.warning("Too much contention for buckets; you may want to look into this.");
        return leastUsed.getId();
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
        settingsManager.set("invitesPaused", "true");
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

    public List<Friend> syncFriends(String userId, Friends clientFriends) {
        Objectify ofy = ofy();
        LanternUser user = ofy.find(LanternUser.class, userId);
        SyncResult result = user.syncFriendsFromClient(clientFriends);
        if (result.shouldSave) {
            ofy.put(user);
        }
        return result.changed;
    }

    public void syncFriend(String userId, Friend clientFriend) {
        //just sync a single friend up from the client
        Objectify ofy = ofy();
        LanternUser user = ofy.find(LanternUser.class, userId);
        if (user.syncFriendFromClient(clientFriend)) {
            ofy.put(user);
        }
    }
}
