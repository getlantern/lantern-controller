package org.lantern.data;

import javax.persistence.Id;

public class GlobalStats {
    @Id
    private String id;
    
    private long bytesServed;
    
    private long bytesProxies;
    
    private long pagesServed;
    
    private long pagesProxied;
    
    private long users;
    
    public GlobalStats() {
        super();
    }

    public GlobalStats(final String id) {
        super();
        this.id = id;
    }

    public String getId() {
        return id;
    }
    
    public long getBytesServed() {
        return bytesServed;
    }

    public void setBytesServed(final long bytesServed) {
        this.bytesServed = bytesServed;
    }

    public long getBytesProxies() {
        return bytesProxies;
    }

    public void setBytesProxies(final long bytesProxies) {
        this.bytesProxies = bytesProxies;
    }

    public long getPagesServed() {
        return pagesServed;
    }

    public void setPagesServed(final long pagesServed) {
        this.pagesServed = pagesServed;
    }

    public long getPagesProxied() {
        return pagesProxied;
    }

    public void setPagesProxied(final long pagesProxied) {
        this.pagesProxied = pagesProxied;
    }

    public void setUsers(final long users) {
        this.users = users;
    }

    public long getUsers() {
        return users;
    }

}
