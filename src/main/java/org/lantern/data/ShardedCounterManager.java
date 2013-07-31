package org.lantern.data;

/*
 * This is loosely based on Google's sharded counter code, but
 * unlike Google's code, it is affordable to run.
 *
 * Copyright (c) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.ArrayList;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.jdo.JDOObjectNotFoundException;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.memcache.ErrorHandlers;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;

/**
 * Manages a set of sharded counters. Counters are lossy because subtotals are
 * maintained in memcache for efficiency.
 */
public class ShardedCounterManager {

    private static final transient Logger log = Logger
            .getLogger(ShardedCounterManager.class.getName());

    //how frequently (in seconds) we persist memcached counters to
    //the durable datastore; this must match the value in cron.xml
    public static final int PERSIST_TIMEOUT = 60;

    public static final int MAX_UPDATES_PER_SHARD_PER_SECOND = 100;

    // how many updates (on average) we record for each shard
    public static final int SHARD_UPDATE_RATIO = 10;

    // This slightly insane constant is required because
    // MemcacheService.increment with negative count will never go below zero.
    // But sometimes we want to go below zero -- for instance, the change in
    // number of users online (this minute) might be negative.
    private static final Long BASELINE = Long.MAX_VALUE / 2;

    private static final Key COUNTERGROUPKEY = KeyFactory.createKey(
            CounterGroup.class.getSimpleName(), CounterGroup.singletonKey);

    private static boolean disabled = false;

    CounterGroup group;

    MemcacheService cache = MemcacheServiceFactory.getMemcacheService();
    
    public static void disable() {
        disabled = true;
    }
    
    public ShardedCounterManager() {
        cache.setErrorHandler(ErrorHandlers.getConsistentLogAndContinue(Level.INFO));
    }

    /**
     * Same as increment(name, 1)
     */
    public void increment(final String name) {
        increment(name, 1);
    }

    /**
     * Same as increment(name, -1)
     */
    final void decrement(final String name) {
        increment(name, -1);
    }

    /**
     * Increment a counter by count.
     *
     * @param name
     * @param count
     */
    final void increment(final String name, long count) {
        if (!loadGroup()) {
            return;
        }
        DatastoreCounter counter = group.getCounter(name);
        if (counter == null) {
            log.log(Level.WARNING, "Trying to increment nonexistent counter " + name);
            return;
        }
        int shardCount = counter.getShardCount();
        if (shardCount <= 0) {
            log.severe("Bogus shard count for " + name + ": " + Integer.toString(shardCount));
            return;
        }
        Random generator = new Random();
        int shardNum = generator.nextInt(shardCount);
        cache.increment("count" + name + "-" + shardNum, count, BASELINE);

        // updates holds the approximate number of updates per minute. This
        // is implemented by incrementing it by N stochastically 1/Nth of the
        // times that the counter updates.

        int dieRoll = generator.nextInt(shardCount * SHARD_UPDATE_RATIO);
        if (dieRoll == 0) {
            cache.increment("updates" + name, shardCount * SHARD_UPDATE_RATIO, 0L);
        }
    }

    private boolean loadGroup() {
        if (disabled) {
            log.warning("NOT CREATING COUNTERS -- SHOULD BE ONLY DURING TESTING!");
            return false;
        }
        if (group != null)
            return true;

        // try to get from cache
        group = (CounterGroup) cache.get("countergroup");
        if (group != null)
            // No need to restore() when reading from memcache.  It's only the
            // Datastore that won't persist the counters hashmap.
            return true;

        log.info("Forced to load counter group from database.  This will be slow.");
        CounterGroup g;
        DatastoreService datastore =
            DatastoreServiceFactory.getDatastoreService();
        for (int tries=10; tries > 0; --tries) {
            Transaction txn = datastore.beginTransaction();
            try {
                final PersistenceManager pm =
                    PMF.get().getPersistenceManager();
                try {
                    g = readGroupFromDatastore(pm);
                    if (g.getNumCounters() == 0) {
                        log.warning("Loading empty countergroup!");
                    } else {
                        log.info("Group has " + g.getNumCounters()
                                 + " counters.");
                    }
                } catch (JDOObjectNotFoundException e) {
                    log.warning("Did not find a counter group."
                        + " Creating a new one. This should only ever happen"
                        + " once.");
                    g = new CounterGroup();
                    writeGroupToDatastore(pm, g);
                }
                pm.close();
                txn.commit();
                group = g;
                // No need to prepareForPersistence when writing to memcache.
                // (And we know persistedCounters is in sync with counters at
                //  this point, BTW).
                cache.put("countergroup", g);
                return true;
            } catch (ConcurrentModificationException e) {
                log.warning("Concurrent modification!");
                // If some thread has concurrently succeeded in loading this
                // group, we don't need to do it again.
                if (group != null) {
                    return true;
                }
            } finally {
                if (txn.isActive()) {
                    txn.rollback();
                }
            }
        }
        throw new RuntimeException("Too much contention for group!");
    }

    public long getCount(final String counterName) {
        if (!loadGroup()) {
            return 0L;
        }
        return group.getCounter(counterName).getCount();
    }

    public void initCounters(Collection<String> timed,
                             Collection<String> untimed) {
        if (!loadGroup()) {
            return;
        }
        // First pass to avoid touching the Datastore if the group has
        // all the names, which will be true most often.
        for (String name : timed) {
            if (group.getCounter(name) == null) {
                actuallyInitCounters(timed, untimed);
                return;
            }
        }
        for (String name : untimed) {
            if (group.getCounter(name) == null) {
                actuallyInitCounters(timed, untimed);
                return;
            }
        }
        log.info("No need.");
    }

    private CounterGroup readGroupFromDatastore(PersistenceManager pm) {
        CounterGroup g = pm.getObjectById(CounterGroup.class, COUNTERGROUPKEY);
        g.restore();
        return g;
    }

    private void writeGroupToDatastore(PersistenceManager pm, CounterGroup g) {
        long now = new Date().getTime() / 1000;
        g.setLastUpdated(now);
        g.prepareForPersistence();
        pm.makePersistent(g);
    }

    private void actuallyInitCounters(Collection<String> timed,
                                      Collection<String> untimed) {
        CounterGroup g;
        DatastoreService datastore =
            DatastoreServiceFactory.getDatastoreService();
        for (int tries=10; tries > 0; --tries) {
            Transaction txn = datastore.beginTransaction();
            try {
                final PersistenceManager pm =
                    PMF.get().getPersistenceManager();
                g = readGroupFromDatastore(pm);
                for (String name : timed) {
                    if (g.getCounter(name) == null) {
                        DatastoreCounter counter =
                            new DatastoreCounter(name, true);
                        g.addCounter(counter);
                    }
                }
                for (String name : untimed) {
                    if (g.getCounter(name) == null) {
                        if ("global.nusers.ever".equals(name)) {
                            log.warning("Creating initial"
                                + " global.nusers.ever; "
                                + "This should only ever happen once.");
                        }
                        DatastoreCounter counter =
                            new DatastoreCounter(name, false);
                        g.addCounter(counter);
                    }
                }
                log.info("Saving group with " + g.getNumCounters()
                         + " counters.");
                writeGroupToDatastore(pm, g);
                pm.close();
                txn.commit();
                invalidateGroupCache();
                return;
            } catch (ConcurrentModificationException e) {
                log.warning("Concurrent modification!");
            } finally {
                if (txn.isActive()) {
                    txn.rollback();
                }
            }
        }
        throw new RuntimeException("Too much contention for group!");
    }

    private void invalidateGroupCache() {
        log.info("Invalidating group cache.");
        group = null;
        cache.delete("countergroup");
    }

    public Map<String, DatastoreCounter> getAllCounters() {
        loadGroup();
        return group.getAllCounters();
    }

    public long getNewCountDestructive(String counterName) {
        loadGroup();
        final DatastoreCounter counter = group.getCounter(counterName);
        final int shardCount = counter.getShardCount();
        long total = 0;
        final ArrayList<String> keys = new ArrayList<String>();
        for (int shardNum = 0; shardNum < shardCount; ++shardNum) {
            keys.add("count" + counterName + "-" + shardNum);
        }
        Map<String, Object> results = cache.getAll(keys);
        for (Map.Entry<String, Object> result : results.entrySet()) {
            total += (Long) result.getValue() - BASELINE;
            cache.delete(result.getKey());
        }
        return total;
    }

    public void updateCounters(Map<String, Long> toReplace,
                               Map<String, Long> toIncrement,
                               Map<String, Integer> toAddShards) {
        DatastoreService datastore =
                DatastoreServiceFactory.getDatastoreService();
        for (int tries=10; tries > 0; --tries) {
            Transaction txn = datastore.beginTransaction();
            try {
                PersistenceManager pm = PMF.get().getPersistenceManager();
                CounterGroup g = readGroupFromDatastore(pm);
                for (Map.Entry<String, Long> entry : toReplace.entrySet()) {
                    g.getCounter(entry.getKey()).setCount(entry.getValue());
                }
                for (Map.Entry<String, Long> entry : toIncrement.entrySet()) {
                    g.getCounter(entry.getKey()).increment(entry.getValue());
                }
                for (Map.Entry<String, Integer> entry
                     : toAddShards.entrySet()) {
                    g.getCounter(entry.getKey()).addShards(entry.getValue());
                }
                writeGroupToDatastore(pm, g);
                pm.close();
                txn.commit();
                if (group.getNumCounters() == 0) {
                    log.warning("Saving an empty countergroup!");
                } else {
                    log.info("Saving group with " + group.getNumCounters()
                             + " counters.");
                }
                invalidateGroupCache();
                return;
            } catch (ConcurrentModificationException e) {
                log.warning("Concurrent modification!");
            } finally {
                if (txn.isActive()) {
                    txn.rollback();
                }
            }
        }
        throw new RuntimeException("Too much contention for group!");
    }

    public long getLastUpdated() {
        loadGroup();
        return group.getLastUpdated();
    }
}