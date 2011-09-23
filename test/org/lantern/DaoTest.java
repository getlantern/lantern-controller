package org.lantern;

import static org.junit.Assert.*;

import java.util.Collection;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.lantern.data.Dao;

import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;

public class DaoTest {
    
    private final LocalServiceTestHelper helper =
            new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());
    
    @Before
    public void setUp() {
        helper.setUp();
    }

    @After
    public void tearDown() {
        helper.tearDown();
    }
    
    @Test
    public void testUsers() {
        final Dao dao = new Dao();
        final String id = "jfdaj";
        dao.addUser(id);
        dao.setAvailable(id, true);
        dao.validate(id);
        final Collection<String> users = dao.getUsers();
        assertEquals(1, users.size());
    }
}

