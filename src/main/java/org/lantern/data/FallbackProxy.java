package org.lantern.data;

import javax.persistence.Id;


public class FallbackProxy {

    @Id
    private String id;

    /**
     * To allow looking a fallback up by IP.
     */
    private String ip;

    private String accessData;

    public enum Status {
        /**
         * Fallback is accepting users.
         */
        active,
        /**
         * Fallback is not accepting users because it's in the process of
         * being split.
         */
        splitting,
        /**
         * Fallback is retired because it's become full.
         *
         * It has already been split.
         */
        full,
        /**
         * Fallback is retired because it's been blocked.
         *
         * If we split it as a result of blocking, that is done.
         */
        blocked
    }

    private Status status = Status.active;

    public FallbackProxy () {};

    public FallbackProxy(String id, String ip, String accessData) {
        this.id = id;
        this.ip = ip;
        this.accessData = accessData;
    }

    public String getId() {
        return id;
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
}
