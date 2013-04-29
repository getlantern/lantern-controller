package org.lantern.data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.jdo.annotations.IdGeneratorStrategy;
import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

@PersistenceCapable(identityType = IdentityType.APPLICATION)
public class CounterGroup implements Serializable {
    private static final long serialVersionUID = -8222330370813841050L;

    @PrimaryKey
    @Persistent(valueStrategy = IdGeneratorStrategy.IDENTITY)
    private Long id;

    @Persistent(serialized = "true")
    private final HashMap<String, DatastoreCounter> counters;

    //in epoch seconds
    @Persistent
    private long lastUpdated;

    public CounterGroup() {
        counters = new HashMap<String, DatastoreCounter>();
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
}
