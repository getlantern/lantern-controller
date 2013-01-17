package org.lantern.data;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

import org.lantern.CensoredUtils;
import org.lantern.LanternControllerConstants;
import org.lantern.LanternUtils;

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
    
    private static final String BYTES_PROXIED = "PROXIED_BYTES";
    private static final String REQUESTS_PROXIED = "PROXIED_REQUESTS";
    private static final String DIRECT_BYTES = "DIRECT_BYTES";
    private static final String DIRECT_REQUESTS = "DIRECT_REQUESTS";
    private static final String CENSORED_USERS = "CENSORED_USERS";
    private static final String UNCENSORED_USERS = "UNCENSORED_USERS";
    private static final String TOTAL_USERS = "TOTAL_USERS";
    private static final String ONLINE = "ONLINE";

    private static final CounterFactory COUNTER_FACTORY = new CounterFactory();
    //private static final Logger LOG = 
    //    Logger.getLogger(Dao.class.getName());
    
    static {
        ObjectifyService.register(LanternUser.class);
        ObjectifyService.register(LanternInstance.class);
        //ObjectifyService.register(Invite.class);
        COUNTER_FACTORY.getOrCreateCounter(BYTES_PROXIED);
        COUNTER_FACTORY.getOrCreateCounter(REQUESTS_PROXIED);
        COUNTER_FACTORY.getOrCreateCounter(DIRECT_BYTES);
        COUNTER_FACTORY.getOrCreateCounter(DIRECT_REQUESTS);
        COUNTER_FACTORY.getOrCreateCounter(CENSORED_USERS);
        COUNTER_FACTORY.getOrCreateCounter(UNCENSORED_USERS);
        COUNTER_FACTORY.getOrCreateCounter(TOTAL_USERS);
        COUNTER_FACTORY.getOrCreateCounter(ONLINE);
    }
    
    public boolean exists(final String id) {
        final Objectify ofy = ofy();
        final LanternUser user = ofy.find(LanternUser.class, id);
        return user != null;
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
        
        log.info("Cutoff data is: "+cutoff);
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

    public void setInstanceAvailable(final String id, final boolean available) {
        final Objectify ofy = ofy();
        final LanternInstance instance = ofy.find(LanternInstance.class, id);
        final boolean originalAvailable;
        if (instance != null) {
            log.info("Setting availability to "+available+" for "+id);
            originalAvailable = instance.getAvailable();
            if (originalAvailable && !available) {
                log.info("Decrementing online count");
                COUNTER_FACTORY.getCounter(ONLINE).decrement();
            } else if (!originalAvailable && available) {
                log.info("Incrementing online count");
                COUNTER_FACTORY.getCounter(ONLINE).increment();
            }
            if (!available) {
                log.info("Deleting instance");
                ofy.delete(instance);
                return;
            }
            
            instance.setAvailable(available);
            instance.setLastUpdated(new Date());
            final LanternUser user = instance.getUser();
            if (user == null) {
                final LanternUser lu = 
                    ofy.find(LanternUser.class, LanternUtils.jidToUserId(id));
                if (lu == null) {
                    log.severe("No user?");
                } else {
                    instance.setUser(lu);
                }
            }
            ofy.put(instance);
            log.info("Finished updating datastore...");
        } else {
            log.info("Could not find instance!!");
            final LanternUser lu = 
                ofy.find(LanternUser.class, LanternUtils.jidToUserId(id));
            if (lu == null) {
                // This probably means the user is censored and unstored. 
                // That's totally normal and expected.
                log.info("Ignoring instance from unknown user");
                return;
            }
            
            final LanternInstance inst = new LanternInstance(id);
            inst.setAvailable(available);
            inst.setUser(lu);
            if (available) {
                log.info("DAO incrementing online count");
                COUNTER_FACTORY.getCounter(ONLINE).increment();
            }
            // We don't decrement if it's not available since we never knew
            // about it anyway, presumably. That should generally not happen.
            ofy.put(inst);
        }
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
    

    public void addInvite(final String sponsor, final String email) {
        final Objectify ofy = ofy();
        final LanternUser user = ofy.find(LanternUser.class, sponsor);
        if (user == null) {
            log.warning("Could not find sponsor sending invite: " +sponsor);
            return;
        }
        log.info("Adding invite to database");
        final LanternUser invitee = new LanternUser(email);
        invitee.setDegree(user.getDegree()+1);
        invitee.setSponsor(sponsor);
        ofy.put(invitee);
        log.info("Finished adding invite...");
    }
    

    public void resaveUser(final String email) {
        final Objectify ofy = ofy();
        Iterable<Key<LanternUser>> allKeys = ofy.query(LanternUser.class).fetchKeys();
        final Map<Key<LanternUser>, LanternUser> all = ofy.get(allKeys);
        final Set<Entry<Key<LanternUser>, LanternUser>> entries = all.entrySet();
        for (final Entry<Key<LanternUser>, LanternUser> entry : entries) {
            final LanternUser user = entry.getValue();
            System.out.println(user.getSponsor());
            user.setDegree(1);
            user.setEverSignedIn(true);
            user.setInvites(0);
            user.setSponsor("adamfisk@gmail.com");
            ofy.put(user);
        }
        
        /*
        final Objectify ofy = ofy();
        final LanternUser user = ofy.find(LanternUser.class, email);
        
        if (user == null) {
            log.warning("Could not find sponsor sending invite: " +user);
            return;
        }
        //invitee.setDegree(user.getDegree()+1);
        //invitee.setSponsor(sponsor);
        user.setDegree(1);
        user.setEverSignedIn(true);
        user.setInvites(5);
        user.setSponsor("adamfisk@gmail");
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
    
    public boolean alreadyInvitedBy(final String inviterEmail, 
        final String invitedEmail) {
        final Objectify ofy = ofy();
        final LanternUser user = ofy.find(LanternUser.class, invitedEmail);
        if (user != null) {
            final String sponsor = user.getSponsor();
            return sponsor.equalsIgnoreCase(inviterEmail.trim());
        }
        return false;
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
        final long bytesProxied, final String countryCode) {
        log.info(
            "Updating user with stats: dr: "+directRequests+" db: "+
            directBytes+" bytesProxied: "+bytesProxied);
        
        final Objectify ofy = ofy();
        final LanternUser user;
        final LanternUser tempUser = ofy.find(LanternUser.class, userId);
        final boolean isUserNew;
        if (tempUser == null) {
            log.info("Could not find user!!");
            user = new LanternUser(userId);
            isUserNew = true;
        } else {
            user = tempUser;
            isUserNew = false;
        }

        user.setBytesProxied(user.getBytesProxied() + bytesProxied);
        user.setRequestsProxied(user.getRequestsProxied() + requestsProxied);
        user.setDirectBytes(user.getDirectBytes() + directBytes);
        user.setDirectRequests(user.getDirectRequests() + directRequests);
        Set<String> newCodes = user.getCountryCodes();
        if (newCodes == null) {
            newCodes = new HashSet<String>();
        }
        newCodes.add(countryCode);
        user.setCountryCodes(newCodes);
        
        // Never store censored users.
        if (!CensoredUtils.isCensored(countryCode)) {
            ofy.put(user);
        }
        
        log.info("Really bumping stats...");
        COUNTER_FACTORY.getCounter(BYTES_PROXIED).increment(bytesProxied);
        COUNTER_FACTORY.getCounter(REQUESTS_PROXIED).increment(requestsProxied);
        COUNTER_FACTORY.getCounter(DIRECT_BYTES).increment(directBytes);
        COUNTER_FACTORY.getCounter(DIRECT_REQUESTS).increment(directRequests);
        if (isUserNew) {
            COUNTER_FACTORY.getCounter(TOTAL_USERS).increment();
            if (CensoredUtils.isCensored(countryCode)) {
                COUNTER_FACTORY.getCounter(CENSORED_USERS).increment();
            } else {
                log.info("Incrementing uncensored count");
                COUNTER_FACTORY.getCounter(UNCENSORED_USERS).increment();
            }
            final ShardedCounter countryCounter = 
                COUNTER_FACTORY.getOrCreateCounter(countryCode);
            countryCounter.increment();
            /*
            final ShardedCounter countryBytesCounter = 
                COUNTER_FACTORY.getOrCreateCounter(countryCode+"-b");
            countryBytesCounter.increment(bytesProxied);
            final ShardedCounter countryCounter = 
                COUNTER_FACTORY.getOrCreateCounter(countryCode);
            final ShardedCounter countryCounter = 
                COUNTER_FACTORY.getOrCreateCounter(countryCode);
            final ShardedCounter countryCounter = 
                COUNTER_FACTORY.getOrCreateCounter(countryCode);
                */
        }
        
        return isUserNew;
    }

    public String getStats() {
        final Map<String, Object> data = new HashMap<String, Object>();
        add(data, BYTES_PROXIED);
        add(data, REQUESTS_PROXIED);
        add(data, DIRECT_BYTES);
        add(data, DIRECT_REQUESTS);
        add(data, CENSORED_USERS);
        add(data, UNCENSORED_USERS);
        add(data, ONLINE);


        final Map<String, Object> countriesData = new HashMap<String, Object>();
        for (final String country : countries) {
            add(countriesData, country);
        }

        return LanternUtils.jsonify(data);
    }

    private void add(final Map<String, Object> data, final String key) {
        final ShardedCounter counter = COUNTER_FACTORY.getCounter(key);
        if (counter == null) {
            add(data, key, 0);
        } else {
            final long count = counter.getCount();
            add(data, key, count);
        }
    }

    private void add(final Map<String, Object> data, final String key, final long val) {
        data.put(key.toLowerCase(), val);
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
}