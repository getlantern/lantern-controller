package org.lantern.data;

import javax.persistence.Id;


public class FallbackProxy {

    @Id
    private String id;

    private String family;

    private String parent;

    /**
     * To allow looking a fallback up by IP.
     */
    private String ip;

    private String accessData;

    /**
     * Comments on this fallback, for the benefit of human admins.
     *
     * E.g. we may use this to why this fallback was retired.
     */
    private String notes;

    public enum Status {
        /**
         * Fallback is still being launched.
         */
        launching,
        /**
         * Fallback is accepting users.
         */
        active,
        /**
         * This fallback is full or blocked and its successors are being
         * launched.
         */
        launchingSuccessors,
        /**
         * Users of this fallback are being reassigned to its successors.
         */
        splitting,
        /**
         * Fallback is retired, probably because it got full or blocked.
         *
         * All its users have been moved to other fallbacks.
         */
        retired,
    }

    private Status status = Status.launching;

    public FallbackProxy() {};

    public FallbackProxy(String id, String parent, String family) {
        this.id = id;
        this.parent = parent;
        this.family = family;
    }

    public String getId() {
        return id;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getIp() {
        return ip;
    }

    public String getAccessData() {
        return accessData;
    }

    public void setAccessData(String accessData) {
        this.accessData = accessData;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }

    public String getNotes() {
        return notes;
    }

    public void addNote(String note) {
        if (notes == null) {
            notes = note;
        } else {
            notes += "\n" + note;
        }
    }
    
    public String getParent() {
    	return parent;
    }
}
