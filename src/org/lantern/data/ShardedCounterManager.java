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
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jdo.PersistenceManager;
import javax.jdo.Query;

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

    public static final int MAX_UPDATES_PER_SHARD = 100;
    // how many updates (on average) we record for each shard
    public static final int SHARD_UPDATE_RATIO = 10;

    // This slightly insane constant is required because
    // MemcacheService.increment with negative count will never go below zero.
    // But sometimes we want to go below zero -- for instance, the change in
    // number of users online (this minute) might be negative.
    private static final Long BASELINE = Long.MAX_VALUE / 2;

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

        final PersistenceManager pm = PMF.get().getPersistenceManager();
        try {
            Query existingCounterQuery = pm.newQuery(CounterGroup.class);
            @SuppressWarnings("unchecked")
            List<CounterGroup> existing = (List<CounterGroup>) existingCounterQuery
                    .execute();
            if (existing.isEmpty()) {
                group = new CounterGroup();
                pm.makePersistent(group);
            } else {
                group = existing.get(0);
            }
        } finally {
            pm.close();
        }

        cache.put("countergroup", group);
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

    public void initCounters(Collection<String> names, boolean timed) {
        loadGroup();

        PersistenceManager pm = PMF.get().getPersistenceManager();

        for (String name : names) {
            if (group.getCounter(name) == null) {
                DatastoreCounter counter = new DatastoreCounter(name, timed);
                group.addCounter(counter);
            }
        }
        cache.put("countergroup", group);
        pm.makePersistent(group);
        pm.close();
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
        cache.put("countergroup", group);

        PersistenceManager pm = PMF.get().getPersistenceManager();
        pm.makePersistent(group);
        pm.close();

    }
}