package org.lantern.data;

import javax.persistence.Id;

public class LanternUser {
    @Id
    private String id;
    
    private boolean available;

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

    public void setAvailable(boolean available) {
        this.available = available;
    }
}
