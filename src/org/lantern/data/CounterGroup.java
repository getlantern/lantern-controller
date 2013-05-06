package org.lantern.data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.NotPersistent;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

import com.google.appengine.api.datastore.Text;

@PersistenceCapable(identityType = IdentityType.APPLICATION)
public class CounterGroup implements Serializable {
    private static final long serialVersionUID = -8222330370813841050L;
    public static final String singletonKey = "cg";

    private static final transient Logger log =
        Logger.getLogger(CounterGroup.class.getName());

    @PrimaryKey
    @Persistent
    private String key;

    @NotPersistent
    private HashMap<String, DatastoreCounter> counters = new HashMap<String, DatastoreCounter>();

    @Persistent
    private Text persistedCounters;

    //in epoch seconds
    @Persistent
    private long lastUpdated;

    public CounterGroup() {
        this(singletonKey);
    }

    public CounterGroup(String key) {
        this.key = key;
    }

    public DatastoreCounter getCounter(String name) {
        return counters.get(name);
    }

    public int getNumCounters() {
        return counters.size();
    }

    public Map<String, DatastoreCounter> getAllCounters() {
        return new HashMap<String, DatastoreCounter>(counters);
    }

    public void addCounter(DatastoreCounter counter) {
        counters.put(counter.getCounterName(), counter);
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public void prepareForPersistence() {
        StringBuilder sb = new StringBuilder(50000);
        for (Map.Entry<String, DatastoreCounter> me : counters.entrySet()) {
            DatastoreCounter counter = me.getValue();
            sb.append(counter.getCounterName());
            sb.append(":");
            sb.append(counter.isTimed() ? 1 : 0);
            sb.append(":");
            sb.append(counter.getCount());
            sb.append(":");
            sb.append(counter.getShardCount());
            sb.append(",");
        }
        String s = sb.toString();
        persistedCounters = new Text(s);
        log.info("Persisted string takes " + s.length() + " characters.");
    }

    public void restore() {
        if (persistedCounters == null) {
            log.info("Nothing to restore.");
            return;
        }
        StringTokenizer st = new StringTokenizer(persistedCounters.getValue(),
                                                 ",");
        String s;
        HashMap<String, DatastoreCounter> c
            = new HashMap<String, DatastoreCounter>(2006);
        try {
            while (true) {
                s = st.nextToken();
                StringTokenizer sti = new StringTokenizer(s, ":");
                String name = sti.nextToken();
                boolean isTimed = sti.nextToken().equals("1");
                DatastoreCounter dc = new DatastoreCounter(name, isTimed);
                dc.setCount(Long.parseLong(sti.nextToken()));
                dc.setShardCount(Integer.parseInt(sti.nextToken()));
                c.put(name, dc);
            }
        } catch (NoSuchElementException e) {}
        counters = c;
        log.info("Got " + getNumCounters() + " counters.");
    }
}
