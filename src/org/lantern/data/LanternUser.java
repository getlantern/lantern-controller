package org.lantern.data;

import java.util.Collection;
import java.util.Date;

import javax.persistence.Id;

public class LanternUser {
    @Id
    private String id;
    
    private boolean available;
    
    private boolean validated;
    
    private long directBytes;
    
    private long bytesProxied;
    
    private long directRequests;
    
    private long requestsProxied;
    
    private Date created = new Date();
    
    private Collection<String> countryCodes;

    public LanternUser() {
        super();
    }

    public LanternUser(final String id) {
        super();
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(final boolean available) {
        this.available = available;
    }

    public void setValidated(final boolean validated) {
        this.validated = validated;
    }

    public boolean isValidated() {
        return validated;
    }

    public void setCreated(final Date created) {
        this.created = created;
    }

    public Date getCreated() {
        return created;
    }

    public long getBytesProxied() {
        return bytesProxied;
    }

    public void setBytesProxied(final long bytesProxied) {
        this.bytesProxied = bytesProxied;
    }

    public Collection<String> getCountryCodes() {
        return countryCodes;
    }

    public void setCountryCodes(final Collection<String> countryCodes) {
        this.countryCodes = countryCodes;
    }
    
    public long getDirectBytes() {
        return directBytes;
    }

    public void setDirectBytes(long directBytes) {
        this.directBytes = directBytes;
    }

    public long getDirectRequests() {
        return directRequests;
    }

    public void setDirectRequests(long directRequests) {
        this.directRequests = directRequests;
    }

    public long getRequestsProxied() {
        return requestsProxied;
    }

    public void setRequestsProxied(long requestsProxied) {
        this.requestsProxied = requestsProxied;
    }
}
