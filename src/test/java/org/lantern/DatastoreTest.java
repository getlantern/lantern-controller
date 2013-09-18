package org.lantern;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.lantern.data.Dao;
import org.lantern.data.ShardedCounterManager;
import org.lantern.state.Friend;
import org.lantern.state.Friend.Status;
import org.lantern.state.Friends;
import org.lantern.state.Mode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.dev.HighRepJobPolicy;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;

public class DatastoreTest {
    
    private final Logger log = LoggerFactory.getLogger(getClass());
    
    private static final class CustomHighRepJobPolicy implements HighRepJobPolicy {
        static int count = 0;

        @Override
        public boolean shouldApplyNewJob(Key entityGroup) {
            // every other new job fails to apply
            return count++ % 2 == 0;
        }

        @Override
        public boolean shouldRollForwardExistingJob(Key entityGroup) {
            // every other exsting job fails to apply
            return count++ % 2 == 0;
        }

    }

    private final LocalServiceTestHelper helper =
        new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig()
          .setAlternateHighRepJobPolicyClass(CustomHighRepJobPolicy.class));

    @Before
    public void setUp() {
        helper.setUp();
    }

    @After
    public void tearDown() {
        helper.tearDown();
    }

    @Test
    public void testEventuallyConsistentGlobalQueryResult() throws Exception {
        ShardedCounterManager.disable();
        final Dao dao = new Dao();
        final String id = "id-7777";
        final String name = "testuser";
        dao.updateUser(id, 0L, 0L, 0L, 0L, "US", name, Mode.get);
        
        final Friend friend1 = new org.lantern.data.ServerFriend("test1@test.com");
        final Friend friend2 = new org.lantern.data.ServerFriend("test2@test.com");
        friend2.setStatus(Status.pending);
        friend2.setLastUpdated(System.currentTimeMillis());
        final Friends friends = new Friends();
        friends.add(friend1);
        friends.add(friend2);
        final List<Friend> changed = dao.syncFriends(name, friends);
        
        
        assertEquals(0, changed.size());
        
        final Friends friends2 = new Friends();
        friend2.setStatus(Status.friend);
        friend2.setLastUpdated(0L);
        friends2.add(friend1);
        friends2.add(friend2);

        
        final List<Friend> changed2 = dao.syncFriends(name, friends2);
        
        // This should still be 0 because the client doesn't need to know 
        // about the change on the server since the client is the one that 
        // initiated the update - it already knows about the change.
        assertEquals(0, changed2.size());
        
        friend2.setStatus(Status.pending);
        
        final List<Friend> changed3 = dao.syncFriends(name, friends2);
        
        // There should actually be an update here because this client is 
        // reporting pending for friend2 but the server has friend2 as already
        // a friend. It's not possible to go from friend to pending, so the
        // server must have newer info!
        assertEquals(1, changed3.size());
    }
}
