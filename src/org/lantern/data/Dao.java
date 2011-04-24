package org.lantern.data;

import java.util.Collection;
import java.util.HashSet;

import com.google.appengine.api.datastore.QueryResultIterator;
import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;
import com.googlecode.objectify.Query;
import com.googlecode.objectify.util.DAOBase;

public class Dao extends DAOBase {
    
    //private static final Logger LOG = 
    //    Logger.getLogger(Dao.class.getName());
    
    static {
        ObjectifyService.register(LanternUser.class);
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
}