package org.lantern.data;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

import org.lantern.ContactsUtil;
import org.lantern.LanternUtils;

import com.google.appengine.api.datastore.QueryResultIterator;
import com.google.appengine.repackaged.org.json.JSONArray;
import com.google.appengine.repackaged.org.json.JSONException;
import com.google.appengine.repackaged.org.json.JSONObject;
import com.google.gdata.util.ServiceException;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.Query;
import com.googlecode.objectify.util.DAOBase;

public class Dao extends DAOBase {
    
    private static final String BYTES_PROXIED = "BYTES_PROXIED";
    private static final String REQUESTS_PROXIED = "REQUESTS_PROXIED";
    private static final String DIRECT_BYTES = "DIRECT_BYTES";
    private static final String DIRECT_REQUESTS = "DIRECT_REQUESTS";
    private static final String CENSORED_USERS = "CENSORED_USERS";
    private static final String UNCENSORED_USERS = "UNCENSORED_USERS";
    private static final String TOTAL_USERS = "TOTAL_USERS";

    private static final CounterFactory COUNTER_FACTORY = new CounterFactory();
    //private static final Logger LOG = 
    //    Logger.getLogger(Dao.class.getName());
    
    static {
        ObjectifyService.register(LanternUser.class);
        COUNTER_FACTORY.getOrCreateCounter(BYTES_PROXIED);
        COUNTER_FACTORY.getOrCreateCounter(REQUESTS_PROXIED);
        COUNTER_FACTORY.getOrCreateCounter(DIRECT_BYTES);
        COUNTER_FACTORY.getOrCreateCounter(DIRECT_REQUESTS);
        COUNTER_FACTORY.getOrCreateCounter(CENSORED_USERS);
        COUNTER_FACTORY.getOrCreateCounter(UNCENSORED_USERS);
    }
    
    public void addUser(final String id) {
        final Objectify ofy = ofy();
        final LanternUser user = ofy.find(LanternUser.class, id);
        if (user == null) {
            System.out.println("Adding user for: "+id);
            ofy.put(new LanternUser(id));
        } else {
            System.out.println("Not adding user "+id);
        }
    }

    public Collection<String> getUsers() {
        final Objectify ofy = ObjectifyService.begin();
        
        // TODO: Limit this more and randomize it more.
        final Query<LanternUser> users = 
            ofy.query(LanternUser.class).filter("available", true).filter("validated", true);
        final Collection<String> results = new HashSet<String>(20);
        final QueryResultIterator<LanternUser> iter = users.iterator();
        while (iter.hasNext()) {
            final LanternUser user = iter.next();
            results.add(user.getId());
        }
        System.out.println("Returning users: "+results);
        return results;
    }

    public void setAvailable(final String id, boolean available) {
        final Objectify ofy = ofy();
        final LanternUser user = ofy.find(LanternUser.class, id);
        if (user != null) {
            System.out.println("Setting availability to "+available+" for "+id);
            user.setAvailable(available);
            ofy.put(user);
        } else {
            System.out.println("Could not find user!!");
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


    public void updateUser(String id, String username, String pwd,
            long directRequests, long directBytes, long requestsProxied,
            long bytesProxied, String machineId, String countryCode) {
        final Objectify ofy = ofy();
        final LanternUser user;
        final LanternUser tempUser = ofy.find(LanternUser.class, id);
        final boolean isNew;
        if (tempUser == null) {
            System.out.println("Could not find user!!");
            user = new LanternUser(id);
            isNew = true;
        } else {
            user = tempUser;
            isNew = false;
        }
        if (!user.isValidated()) {
            try {
                if (ContactsUtil.appearsToBeReal(username, pwd)) {
                    user.setValidated(true);
                    ofy.put(user);
                }
            } catch (final IOException e) {
                e.printStackTrace();
            } catch (final ServiceException e) {
                e.printStackTrace();
            }
        }
        user.setBytesProxied(user.getBytesProxied() + bytesProxied);
        user.setRequestsProxied(user.getRequestsProxied() + requestsProxied);
        user.setDirectBytes(user.getDirectBytes() + directBytes);
        user.setDirectRequests(user.getDirectRequests() + directRequests);
        final Collection<String> newCodes = user.getCountryCodes();
        newCodes.add(countryCode);
        user.setCountryCodes(newCodes);
        ofy.put(user);
        
        COUNTER_FACTORY.getCounter(BYTES_PROXIED).increment(bytesProxied);
        COUNTER_FACTORY.getCounter(REQUESTS_PROXIED).increment(requestsProxied);
        COUNTER_FACTORY.getCounter(DIRECT_BYTES).increment(directBytes);
        COUNTER_FACTORY.getCounter(DIRECT_REQUESTS).increment(directRequests);
        if (isNew) {
            COUNTER_FACTORY.getCounter(TOTAL_USERS).increment();
            if (LanternUtils.isCensored(countryCode)) {
                COUNTER_FACTORY.getCounter(CENSORED_USERS).increment();
            } else {
                COUNTER_FACTORY.getCounter(UNCENSORED_USERS).increment();
            }
            final ShardedCounter countryCounter;
            final ShardedCounter tempCc = COUNTER_FACTORY.getCounter(countryCode);
            if (tempCc == null) {
                countryCounter = COUNTER_FACTORY.createCounter(countryCode);
            } else {
                countryCounter = tempCc;
            }
            countryCounter.increment();
        }
    }

    public JSONObject getStats() {
        final JSONObject json = new JSONObject();
        add(json, BYTES_PROXIED);
        add(json, REQUESTS_PROXIED);
        add(json, DIRECT_BYTES);
        add(json, DIRECT_REQUESTS);
        add(json, CENSORED_USERS);
        add(json, UNCENSORED_USERS);
        return json;
    }

    private void add(final JSONObject json, final String key) {
        try {
            json.put(key.toLowerCase(), COUNTER_FACTORY.getOrCreateCounter(key).getCount());
        } catch (final JSONException e) {
            e.printStackTrace();
        }
    }

    public void whitelistAdditions(final JSONArray whitelistAdditions,
        final String countryCode) {
        final int length = whitelistAdditions.length();
        for (int i = 0; i < length; i++) {
            try {
                final String url = (String) whitelistAdditions.get(i);
                final WhitelistEntry entry = new WhitelistEntry();
                entry.setUrl(url);
                entry.setCountryCode(countryCode);
            } catch (final JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public void whitelistRemovals(final JSONArray whitelistRemovals,
        final String countryCode) {
        final int length = whitelistRemovals.length();
        for (int i = 0; i < length; i++) {
            try {
                final String url = (String) whitelistRemovals.get(i);
                final WhitelistRemovalEntry entry = new WhitelistRemovalEntry();
                entry.setUrl(url);
                entry.setCountryCode(countryCode);
            } catch (final JSONException e) {
                e.printStackTrace();
            }
        }
    }
    
}