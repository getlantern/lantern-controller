package org.lantern;

import java.io.IOException;
import java.io.OutputStream;
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
            final HttpServletResponse response) throws IOException {
        response.setContentType("text/plain");

        MemcacheService cache = MemcacheServiceFactory.getMemcacheService();

        // get cached counters
        ShardedCounterManager manager = new ShardedCounterManager();
        Map<String, DatastoreCounter> counters = manager.getAllCounters();
        Map<String, Long> cachedCounters = new HashMap<String, Long>();
        for (DatastoreCounter counter : counters.values()) {
            // get count of new items since last persist
            String counterName = counter.getCounterName();
            long count = manager.getNewCountDestructive(counterName);
            if (counter.isTimed()) {
                // timed counters just get the current count
                counter.setCount(count);
            } else {
                counter.increment(count);
            }
            // and preemptively cache the current count
            cachedCounters.put("count" + counterName, counter.getCount());

            // check the update counter to see if we need new shards
            Long updates = (Long) cache.get("updates" + counterName);
            if (updates != null) {
                cachedCounters.put("updates" + counterName, 0L);
                final int currentShards = counter.getShardCount();
                final int maxUpdatesPerShard = ShardedCounterManager.MAX_UPDATES_PER_SHARD;
                if (updates > currentShards * maxUpdatesPerShard) {
                    log.info("adding shard for counter " + counterName);
                    int notHandledByCurrentShards = (int) (updates
                            - (currentShards * maxUpdatesPerShard));
                    counter.addShards((int) Math.ceil(notHandledByCurrentShards / (float)maxUpdatesPerShard));
                }
            }
        }
        // store and cache the new counter values
        cache.putAll(cachedCounters);
        manager.persistCounters();
        final Dao dao = new Dao();

        //prewarm cache for stats page
        String stats = dao.getStats();
        cache.put("statsJson", stats, Expiration.byDeltaSeconds(ShardedCounterManager.PERSIST_TIMEOUT));

        response.setStatus(HttpServletResponse.SC_OK);

        final byte[] content = "OK".getBytes("UTF-8");
        response.setContentLength(content.length);

        final OutputStream os = response.getOutputStream();

        os.write(content);
        os.flush();
    }
}
