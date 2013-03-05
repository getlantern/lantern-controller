package org.lantern.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.lantern.CensoredUtils;
import org.lantern.InvitedServerLauncher;
import org.lantern.JsonUtils;
import org.lantern.LanternControllerConstants;
import org.lantern.LanternControllerUtils;

import com.google.appengine.api.datastore.QueryResultIterator;
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
    private static final String GIVE = "give";
    private static final String GET = "get";
    private static final String BPS = "bps";
    private static final String GLOBAL = "global";

    private static final ShardedCounterManager COUNTER_MANAGER = new ShardedCounterManager();

    private static final int TXN_RETRIES = 10;

    static {
        ObjectifyService.register(LanternUser.class);
        ObjectifyService.register(LanternInstance.class);
        ObjectifyService.register(Invite.class);
        ObjectifyService.register(InstallerBucket.class);

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
        COUNTER_MANAGER.initCounters(counters, false);
        COUNTER_MANAGER.initCounters(timedCounters, true);
    }

    public boolean exists(final String id) {
        final Objectify ofy = ofy();
        final LanternUser user = ofy.find(LanternUser.class, id);
        return user != null;
    }

    public Collection<LanternUser> getAllUsers() {
        final Objectify ofy = ObjectifyService.begin();
        final Query<LanternUser> users = ofy.query(LanternUser.class);
        return users.list();
    }

    public Collection<String> getInstances() {
        final Objectify ofy = ObjectifyService.begin();

        // TODO: Limit this more and randomize it more.
        // First find the users that are validated, and then find the
        // instances associated with that user that are available.
        final long now = System.currentTimeMillis();
        //final Date cutoff = new Date(now - 1000 * 60 * 60 * 24 * 5);

        // Instances get updated quite often via info chat messages. These are
        // more reliable than presence updates because we don't get presence
        // updates from users in invisibility mode.
        // Only give out the freshest instances.
        final Date cutoff =
            new Date(now - LanternControllerConstants.UPDATE_TIME_MILLIS);

        log.info("Cutoff date is: "+cutoff);
        final Query<LanternInstance> instances =
            ofy.query(LanternInstance.class).filter("available", true).filter("lastUpdated >", cutoff);
        //final Query<LanternInstance> instances =
        //    ofy.query(LanternInstance.class).filter("available", true);

        //final Query<LanternUser> users =
        //    ofy.query(LanternUser.class).filter("available", true).filter("validated", true);
        final Collection<String> results = new HashSet<String>(20);
        final QueryResultIterator<LanternInstance> iter = instances.iterator();
        while (iter.hasNext()) {
            final LanternInstance user = iter.next();
            results.add(user.getId());
        }
        log.info("Returning instances: "+results);
        return results;
    }

    public void setInstanceAvailable(String userId, final String instanceId,
            final String countryCode, final boolean isGiveMode) {
        final Objectify ofy = ofy();
        // As of this writing, we use instanceId to refer to the XMPP
        // resource, that being the instance-specific part of the jabberId.
        // Note that this does *not* identify an instance globally.  You need
        // the userId too.  That is why, somewhat confusingly, instances are
        // keyed by full jabberId in the LanternInstances table.
        final String
            fullId = LanternControllerUtils.jabberIdFromUserAndResource(
                        userId, instanceId);
        LanternInstance instance = ofy.find(LanternInstance.class, fullId);
        String giveStr = isGiveMode ? GIVE : GET;
        LanternUser user;
        if (instance != null) {
            log.info("Setting availability to true for " + fullId);
            user = ofy.find(LanternUser.class, instance.getUser());
            if (!instance.isAvailable()) {
                log.info("Incrementing online count");

                //handle the online counters
                incrementCounter(dottedPath(GLOBAL, NUSERS, ONLINE));
                incrementCounter(dottedPath(countryCode, NPEERS, ONLINE, giveStr));

                //and the ever-seen
                if (!instance.getSeenFromCountry(countryCode)) {
                    instance.addSeenFromCountry(countryCode);
                    incrementCounter(dottedPath(countryCode, NPEERS, EVER, giveStr));
                    incrementCounter(dottedPath(GLOBAL, NPEERS, EVER, giveStr));
                }
                instance.setCurrentCountry(countryCode);

                //notice that we check for any signed in before we set this instance
                //available
                if (!user.anyInstancesSignedIn()) {
                    incrementCounter(dottedPath(countryCode, NUSERS, ONLINE));
                    incrementCounter(dottedPath(GLOBAL, NUSERS, ONLINE));
                }
                instance.setAvailable(true);
                instance.setLastUpdated(new Date());
                user.incrementInstancesSignedIn();
                ofy.put(instance);
                ofy.put(user);
                log.info("Finished updating datastore...");
            }

            assert(user != null);
        } else {
            log.info("Could not find instance!!");
            user = ofy.find(LanternUser.class, userId);

            assert(user != null);
            instance = new LanternInstance(fullId);
            instance.setUser(userId);
            instance.setAvailable(true);
            instance.setCurrentCountry(countryCode);
            log.info("DAO incrementing online count");
            incrementCounter(dottedPath(GLOBAL, NUSERS, ONLINE));
            incrementCounter(dottedPath(GLOBAL, NPEERS, ONLINE, giveStr));
            incrementCounter(dottedPath(GLOBAL, NPEERS, EVER, giveStr));
            incrementCounter(dottedPath(countryCode, NUSERS, ONLINE));
            incrementCounter(dottedPath(countryCode, NPEERS, ONLINE, giveStr));
            incrementCounter(dottedPath(countryCode, NPEERS, EVER, giveStr));
            ofy.put(instance);
            ofy.put(user);
            log.info("Finished updating datastore...");
        }

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

    /**
     * Returns true if the invite was added, false if it wasn't (because it
     * already existed, for instance)
     *
     * @param sponsor
     * @param email
     * @return
     */
    public boolean addInvite(final String sponsor, final String email) {
        for (int retries=TXN_RETRIES; retries>0; retries--) {
            final Objectify ofy = ObjectifyService.beginTransaction();
            try {
                final LanternUser inviter = ofy.find(LanternUser.class, sponsor);
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

                LanternUser invitee = ofy.find(LanternUser.class, email);
                if (invitee == null) {
                    log.info("Adding invite to database");
                    invitee = new LanternUser(email);

                    invitee.setDegree(inviter.getDegree() + 1);
                    if (invitee.getDegree() < 3 && invitee.getInvites() < 2) {
                        invitee.setInvites(2);
                    }
                    invitee.setSponsor(sponsor);
                    ofy.put(invitee);
                    log.info("Finished adding invite...");
                } else {
                    log.info("Invitee exists, nothing to do here");
                }
                ofy.getTxn().commit();
                log.info("Done committing");
                return true;
            } catch (Exception e) {
                log.log(Level.WARNING, "txn commit failed in some way {}", e);
                continue;
            } finally {
                if (ofy.getTxn().isActive()) {
                    ofy.getTxn().rollback();
                }
            }
        }
        return false;
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
        final Objectify ofy = ofy();
        final LanternUser user = ofy.find(LanternUser.class, userId);
        if (user == null) {
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
    }

    public boolean alreadyInvitedBy(Objectify ofy, final String inviterEmail,
        final String invitedEmail) {
        String key = Invite.makeKey(inviterEmail, invitedEmail);
        final Invite invite = ofy.find(Invite.class, key);
        if (invite != null) return true;

        //handle legacy invites
        final LanternUser user = ofy.find(LanternUser.class, invitedEmail);
        if (user == null) return false;
        final String sponsor = user.getSponsor();
        return emailsMatch(sponsor, inviterEmail);
    }

    public boolean isInvited(final String email) {
        final Objectify ofy = ofy();
        final LanternUser user = ofy.find(LanternUser.class, email);
        return user != null;
    }

    public void updateLastAccessed(final String email) {
        final Objectify ofy = ofy();
        final LanternUser user = ofy.find(LanternUser.class, email);

        if (user != null) {
            user.setLastAccessed(new Date());
            ofy.put(user);
        }
    }

    public boolean updateUser(final String userId, final long directRequests,
        final long directBytes, final long requestsProxied,
        final long bytesProxied, final String countryCode,
        final String instanceId, boolean isGiveMode) {
        log.info(
            "Updating user with stats: dr: "+directRequests+" db: "+
            directBytes+" bytesProxied: "+bytesProxied);

        final Objectify ofy = ofy();
        final LanternUser user;
        final LanternUser tempUser = ofy.find(LanternUser.class, userId);
        final boolean isUserNew = tempUser == null;
        if (isUserNew) {
            log.info("Could not find user!!");
            user = new LanternUser(userId);
        } else {
            user = tempUser;
        }

        user.setBytesProxied(user.getBytesProxied() + bytesProxied);
        user.setRequestsProxied(user.getRequestsProxied() + requestsProxied);
        user.setDirectBytes(user.getDirectBytes() + directBytes);
        user.setDirectRequests(user.getDirectRequests() + directRequests);

        ofy.put(user);

        log.info("Really bumping stats...");

        String giveStr = isGiveMode ? GIVE : GET;
        if (!user.instanceIdSeen(instanceId)) {
            incrementCounter(dottedPath(NPEERS, EVER, giveStr));
        }
        if (!user.countrySeen(countryCode)) {
            incrementCounter(dottedPath(countryCode, NUSERS, EVER));
        }

        // Never store censored users.
        if (!CensoredUtils.isCensored(countryCode)) {
            ofy.put(user);
        }

        incrementCounter(dottedPath(countryCode, BYTES_EVER), bytesProxied);
        incrementCounter(dottedPath(GLOBAL, BYTES_EVER), bytesProxied);

        incrementCounter(dottedPath(countryCode, BPS), bytesProxied);
        incrementCounter(dottedPath(GLOBAL, BPS), bytesProxied);

        incrementCounter(REQUESTS_PROXIED, requestsProxied);
        incrementCounter(DIRECT_BYTES, directBytes);
        incrementCounter(DIRECT_REQUESTS, directRequests);
        if (isUserNew) {
            COUNTER_MANAGER.increment(TOTAL_USERS);
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

    private void incrementCounter(String counter) {
        COUNTER_MANAGER.increment(counter);
    }

    private void incrementCounter(String counter, long count) {
        COUNTER_MANAGER.increment(counter, count);
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
            long count = COUNTER_MANAGER.getCount(counterName);
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

    public boolean isEverSignedIn(final String email) {
        final Objectify ofy = ofy();
        final LanternUser invite = ofy.find(LanternUser.class, email);
        if (invite == null) {
            log.severe("No corresponding invite for "+email);
            return false;
        }
        return invite.isEverSignedIn();
    }

    public void signedIn(final String email) {
        final Objectify ofy = ofy();
        final LanternUser invite = ofy.find(LanternUser.class, email);
        if (invite == null) {
            log.severe("No corresponding invite for "+email);
            return;
        }
        invite.setEverSignedIn(true);
        ofy.put(invite);
    }

    public void setInstanceUnavailable(String userId, String instanceId, boolean isGiveMode) {
        final Objectify ofy = ofy();
        // As of this writing, we use instanceId to refer to the XMPP
        // resource, that being the instance-specific part of the jabberId.
        // Note that this does *not* identify an instance globally.  You need
        // the userId too.  That is why, somewhat confusingly, instances are
        // keyed by full jabberId in the LanternInstances table.
        final String
            fullId = LanternControllerUtils.jabberIdFromUserAndResource(
                        userId, instanceId);
        final LanternInstance instance = ofy.find(LanternInstance.class, fullId);
        if (instance == null) {
            log.warning("Instance " + fullId + " not available.");
            return;
        }
        if (instance.isAvailable()) {
            log.info("Decrementing online count");
            instance.setAvailable(false);
            LanternUser user = ofy.find(LanternUser.class, userId);
            user.decrementInstancesSignedIn();

            String giveStr = isGiveMode ? GIVE : GET;
            String countryCode = instance.getCurrentCountry();

            COUNTER_MANAGER.decrement(dottedPath(GLOBAL, NPEERS, ONLINE, giveStr));
            COUNTER_MANAGER.decrement(dottedPath(countryCode, NPEERS, ONLINE, giveStr));

            if (user.anyInstancesSignedIn()) {
                COUNTER_MANAGER.decrement(dottedPath(GLOBAL, NUSERS, ONLINE));
                COUNTER_MANAGER.decrement(dottedPath(countryCode, NUSERS, ONLINE));
            }

            ofy.put(instance);
            ofy.put(user);
        }
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

    public Collection<String> setInstallerLocationAndGetInvitees(
            final String inviterEmail, final String installerLocation)
            throws UnknownUserException {
    	Collection<String> results = new HashSet<String>(20);
        final Key<LanternUser>
            ancestor = new Key<LanternUser>(LanternUser.class, inviterEmail);

    	for (int retries=TXN_RETRIES; retries > 0; retries--) {
            results = new HashSet<String>(20);

            // Compatibility with old invites.
            final Query<LanternUser> invitees =
                // Note that this does NOT have any transactional
                // guarantees.  This is not an ancestor query and thus
                // we couldn't run it in the transaction.
                ofy().query(LanternUser.class).filter("sponsor", inviterEmail);
            for (LanternUser invitee : invitees) {
                final String invitedEmail = invitee.getId();
                if (!emailsMatch(invitedEmail, inviterEmail)) {
                    results.add(invitedEmail);
                }
            }

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
    	//XXX: is really returning our best guess better than failing?
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
}

