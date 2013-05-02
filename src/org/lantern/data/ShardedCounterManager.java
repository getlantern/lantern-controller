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

    CounterGroup group;

    MemcacheService cache = MemcacheServiceFactory.getMemcacheService();

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
        loadGroup();
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

    private void loadGroup() {
        if (group != null)
            return;

        // try to get from cache
        group = (CounterGroup) cache.get("countergroup");
        if (group != null)
            return;

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
                    g = pm.getObjectById(CounterGroup.class, COUNTERGROUPKEY);
                    g.restore();
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
                    pm.makePersistent(g);
                }
                pm.close();
                txn.commit();
                group = g;
                cache.put("countergroup", g);
                return;
            } catch (ConcurrentModificationException e) {
                log.warning("Concurrent modification!");
                // If some thread has concurrently succeeded in loading this
                // group, we don't need to do it again.
                if (group != null) {
                    return;
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
        Long cachedCount = (Long) cache.get("count" + counterName);
        if (cachedCount != null) {
            return cachedCount;
        }
        loadGroup();
        DatastoreCounter counter = group.getCounter(counterName);
        long count = counter.getCount();

        cache.put("count" + counterName, count);
        return count;
    }

    public void initCounters(Collection<String> timed,
                             Collection<String> untimed) {
        loadGroup();
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
                g = pm.getObjectById(CounterGroup.class, COUNTERGROUPKEY);
                g.restore();
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
                g.prepareForPersistance();
                pm.makePersistent(g);
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

    public void persistCounters() {
        DatastoreService datastore =
                DatastoreServiceFactory.getDatastoreService();
        for (int tries=10; tries > 0; --tries) {
            // Note this transaction doesn't protect you from saving a
            // version of the group older than that in the datastore.
            // As of this writing (git blame me) I believe this is not a
            // problem because the only concurrent write to the group comes
            // from initCounters, and PersistController has taken care to
            // call that before calling this.  There is still a possible race
            // condition, but the only thing we'd miss would be the first
            // minute worth of updates for a newly created counter.
            Transaction txn = datastore.beginTransaction();
            try {
                PersistenceManager pm = PMF.get().getPersistenceManager();
                long now = new Date().getTime() / 1000;
                group.setLastUpdated(now);
                group.prepareForPersistance();
                pm.makePersistent(group);
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