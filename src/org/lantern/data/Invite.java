package org.lantern.data;

import javax.persistence.Id;

public class Invite {

    @Id
    private String id;
    
    /**
     * This is the number of degrees this user is from the original core of
     * Lantern developers.
     */
    private int degree;
    
    /**
     * This is the user who invited this user to the network.
     */
    private String sponsor;
    
    public Invite() {
        super();
    }
    
    public Invite(final String id) {
        super();
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setDegree(int degree) {
        this.degree = degree;
    }

    public int getDegree() {
        return degree;
    }

    public void setSponsor(String sponsor) {
        this.sponsor = sponsor;
    }

    public String getSponsor() {
        return sponsor;
    }
}
