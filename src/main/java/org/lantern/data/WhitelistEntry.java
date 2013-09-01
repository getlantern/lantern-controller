package org.lantern.data;

import java.util.Date;

import javax.persistence.Id;

public class WhitelistEntry {
    
    @Id
    private String id;
    
    private Date created = new Date();
    
    private String url;
    
    private String countryCode;
    
    public WhitelistEntry() {
        super();
    }

    public WhitelistEntry(final String id) {
        super();
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setCreated(final Date created) {
        this.created = created;
    }

    public Date getCreated() {
        return created;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(final String countryCode) {
        this.countryCode = countryCode;
    }
}
