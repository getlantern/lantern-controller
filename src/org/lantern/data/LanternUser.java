package org.lantern.data;

import javax.persistence.Id;

public class LanternUser {
    @Id
    private String id;
    
    private boolean available;
    
    private boolean validated;

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
}
