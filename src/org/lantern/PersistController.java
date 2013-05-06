package org.lantern;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.lantern.data.Dao;
import org.lantern.data.DatastoreCounter;
import org.lantern.data.ShardedCounterManager;

import com.google.appengine.api.memcache.Expiration;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;

@SuppressWarnings("serial")
public class PersistController extends HttpServlet {
    private static final transient Logger log = Logger
            .getLogger(PersistController.class.getName());

    @Override
    public void doGet(final HttpServletRequest request,
            final HttpServletResponse response) {

        // If Dao needs to initialize the counters, let that happen before our
        // own ShardedCounterManager fetches them.
        new Dao();
        MemcacheService cache = MemcacheServiceFactory.getMemcacheService();

        // get cached counters
        ShardedCounterManager manager = new ShardedCounterManager();
        // Used only for the list of counter names.
        //
        // Any counters that are added (via
        // ShardedCounterManager.initCounters) concurrently after this point
        // will be handled in the next persistence cycle.
        Map<String, DatastoreCounter> counters = manager.getAllCounters();
        Map<String, Long> cacheUpdates = new HashMap<String, Long>();
        long now = new Date().getTime() / 1000;
        int timeSinceLastPersist = (int) (now - manager.getLastUpdated());
        log.info("Time since last cycle  " + timeSinceLastPersist);
        if (timeSinceLastPersist < ShardedCounterManager.PERSIST_TIMEOUT) {
            //assume at least one cycle has passed to avoid
            //weirdness
            log.warning("Not enough time has passed between persist cycles; setting to "
                    + ShardedCounterManager.PERSIST_TIMEOUT);
            timeSinceLastPersist = ShardedCounterManager.PERSIST_TIMEOUT;
        }

        // Collect operations once, out of transactional context.  Any updates
        // to a given counter after we call getNewCountDestructive for it will
        // be handled in the next persistence cycle.
        Map<String, Long> toIncrement = new HashMap<String, Long>();
        Map<String, Long> toReplace = new HashMap<String, Long>();
        Map<String, Integer> toAddShards = new HashMap<String, Integer>();
        for (DatastoreCounter counter : counters.values()) {
            // get count of new items since last persist
            String counterName = counter.getCounterName();
            long count = manager.getNewCountDestructive(counterName);
            if (counter.isTimed()) {
                // timed counters just get the current count
                toReplace.put(counterName,
                              (count * ShardedCounterManager.PERSIST_TIMEOUT)
                              / timeSinceLastPersist);
            } else {
                if (count != 0) {
                    log.info("Counter '" + counterName + "': "
                            + "was " + counter.getCount()
                            + ", incremented by " + count);
                    toIncrement.put(counterName, count);
                }
            }
            // check the update counter to see if we need new shards
            Long updates = (Long) cache.get("updates" + counterName);
            if (updates != null) {
                cacheUpdates.put("updates" + counterName, 0L);
                final int currentShards = counter.getShardCount();
                final int maxUpdatesPerShard = ShardedCounterManager.MAX_UPDATES_PER_SHARD_PER_SECOND
                        * timeSinceLastPersist;
                if (updates > currentShards * maxUpdatesPerShard) {
                    log.info("adding shard for counter " + counterName);
                    int notHandledByCurrentShards = (int) (updates
                            - (currentShards * maxUpdatesPerShard));
                    toAddShards.put(counterName,
                                    (int) Math.ceil(notHandledByCurrentShards
                                          / (float)maxUpdatesPerShard));
                }
            }
        }
        cache.putAll(cacheUpdates);
        manager.updateCounters(toReplace, toIncrement, toAddShards);
        final Dao dao = new Dao();

        //prewarm cache for stats page
        String stats = dao.getStats();
        cache.put("statsJson", stats, Expiration.byDeltaSeconds(ShardedCounterManager.PERSIST_TIMEOUT));

        LanternControllerUtils.populateOKResponse(response, "OK");
    }
}
