package org.lantern.data;

import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import org.lantern.CensoredUtils;
import org.lantern.LanternConstants;
import org.lantern.LanternUtils;

import com.google.appengine.api.datastore.QueryResultIterator;
import com.google.appengine.repackaged.org.json.JSONException;
import com.google.appengine.repackaged.org.json.JSONObject;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.Query;
import com.googlecode.objectify.util.DAOBase;

public class Dao extends DAOBase {
    
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
            new Date(now - LanternConstants.UPDATE_TIME_MILLIS);
        
        final Query<LanternInstance> instances = 
            ofy.query(LanternInstance.class).filter("available", true).filter("lastUpdated >", cutoff).filter(
                "user.validated", true);
        
        //final Query<LanternUser> users = 
        //    ofy.query(LanternUser.class).filter("available", true).filter("validated", true);
        final Collection<String> results = new HashSet<String>(20);
        final QueryResultIterator<LanternInstance> iter = instances.iterator();
        while (iter.hasNext()) {
            final LanternInstance user = iter.next();
            results.add(user.getId());
        }
        System.out.println("Returning instances: "+results);
        return results;
    }

    public void setInstanceAvailable(final String id, final boolean available) {
        final Objectify ofy = ofy();
        final LanternInstance instance = ofy.find(LanternInstance.class, id);
        final boolean originalAvailable;
        if (instance != null) {
            System.out.println("Setting availability to "+available+" for "+id);
            originalAvailable = instance.getAvailable();
            if (originalAvailable && !available) {
                System.out.println("DAO::decrementing");
                COUNTER_FACTORY.getCounter(ONLINE).decrement();
            } else if (!originalAvailable && available) {
                System.out.println("DAO::incrementing");
                COUNTER_FACTORY.getCounter(ONLINE).increment();
            }
            if (!available) {
                System.out.println("Deleting instance");
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
                    System.err.println("Dao::setInstanceAvailable::no user?");
                } else {
                    instance.setUser(lu);
                }
            }
            ofy.put(instance);
            System.out.println("Finished updating datastore...");
        } else {
            System.out.println("Could not find instance!!");
            final LanternUser lu = 
                ofy.find(LanternUser.class, LanternUtils.jidToUserId(id));
            if (lu == null) {
                // This probably means the user is censored and unstored. 
                // That's totally normal and expected.
                System.out.println("Ignoring instance from unknown user");
                return;
            }
            
            final LanternInstance inst = new LanternInstance(id);
            inst.setAvailable(available);
            inst.setUser(lu);
            if (available) {
                System.out.println("DAO incrementing online count");
                COUNTER_FACTORY.getCounter(ONLINE).increment();
            }
            // We don't decrement if it's not available since we never knew
            // about it anyway, presumably. That should generally not happen.
            ofy.put(inst);
        }
    }
    
    public void validate(final String id) {
        final Objectify ofy = ofy();
        final LanternUser user = ofy.find(LanternUser.class, id);
        if (user != null) {
            user.setValidated(true);
            ofy.put(user);
        } else {
            System.out.println("Could not find user!!");
        }
    }


    public boolean updateUser(final String fullId, final long directRequests, 
        final long directBytes, final long requestsProxied,
        final long bytesProxied, final String countryCode) {
        System.out.println(
            "Updating user with stats: dr: "+directRequests+" db: "+
            directBytes+" bytesProxied: "+bytesProxied);
        
        final String userId = LanternUtils.jidToUserId(fullId);
        
        final Objectify ofy = ofy();
        final LanternUser user;
        final LanternUser tempUser = ofy.find(LanternUser.class, userId);
        final boolean isUserNew;
        if (tempUser == null) {
            System.out.println("Could not find user!!");
            user = new LanternUser(userId);
            isUserNew = true;
        } else {
            user = tempUser;
            isUserNew = false;
        }
        
        /*
        final String instanceId = LanternUtils.jidToInstanceId(fullId);
        final LanternInstance instance;
        final LanternInstance tempInstance = ofy.find(LanternInstance.class, instanceId);
        final boolean isInstanceNew;
        if (tempInstance == null) {
            System.out.println("Could not find user!!");
            instance = new LanternInstance(instanceId);
            isInstanceNew = true;
        } else {
            instance = tempInstance;
            isInstanceNew = false;
        }
        */
        user.setValidated(true);
        /*
        if (!user.isValidated()) {
            try {
                final int contacts = ContactsUtil.getNumContacts(username, pwd);
                if (contacts > 600) {
                    user.setValidated(true);
                }
                
                // Don't store the exact number of contacts.
                user.setNumContacts(Math.round(contacts/50) * 50); 
            } catch (final IOException e) {
                e.printStackTrace();
            } catch (final ServiceException e) {
                e.printStackTrace();
            }
        }
        */
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
        
        System.out.println("Really bumping stats...");
        COUNTER_FACTORY.getCounter(BYTES_PROXIED).increment(bytesProxied);
        COUNTER_FACTORY.getCounter(REQUESTS_PROXIED).increment(requestsProxied);
        COUNTER_FACTORY.getCounter(DIRECT_BYTES).increment(directBytes);
        COUNTER_FACTORY.getCounter(DIRECT_REQUESTS).increment(directRequests);
        if (isUserNew) {
            COUNTER_FACTORY.getCounter(TOTAL_USERS).increment();
            if (CensoredUtils.isCensored(countryCode)) {
                COUNTER_FACTORY.getCounter(CENSORED_USERS).increment();
            } else {
                System.out.println("Adding uncensored user");
                COUNTER_FACTORY.getCounter(UNCENSORED_USERS).increment();
            }
            final ShardedCounter countryCounter = 
                COUNTER_FACTORY.getOrCreateCounter(countryCode);
            countryCounter.increment();
        }
        
        return isUserNew;
    }

    public JSONObject getStats() {
        final JSONObject json = new JSONObject();
        add(json, BYTES_PROXIED);
        add(json, REQUESTS_PROXIED);
        add(json, DIRECT_BYTES);
        add(json, DIRECT_REQUESTS);
        add(json, CENSORED_USERS);
        add(json, UNCENSORED_USERS);
        add(json, ONLINE);
        return json;
    }

    private void add(final JSONObject json, final String key) {
        final long count = COUNTER_FACTORY.getCounter(key).getCount();
        try {
            json.put(key.toLowerCase(), count);
        } catch (final JSONException e) {
            e.printStackTrace();
        }
    }

    public void whitelistAdditions(final Collection<String> whitelistAdditions,
        final String countryCode) {
        for (final String url : whitelistAdditions) {
            final WhitelistEntry entry = new WhitelistEntry();
            entry.setUrl(url);
            entry.setCountryCode(countryCode);
        }
    }

    public void whitelistRemovals(final Collection<String> whitelistRemovals,
        final String countryCode) {
        for (final String url : whitelistRemovals) {
            final WhitelistRemovalEntry entry = new WhitelistRemovalEntry();
            entry.setUrl(url);
            entry.setCountryCode(countryCode);
        }
    }
}