package org.lantern.data;

import javax.persistence.Id;

import org.lantern.LanternVersion;
import org.lantern.SemanticVersion;

public class LatestLanternVersion extends LanternVersion {
    public final static String SINGLETON_KEY = "latest";

    /**
     * We use the "id" field for the version string, but we don't key
     * by it in the datastore since this is a singleton.
     */
    @Id
    private String key = SINGLETON_KEY;

    private String id;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
        SemanticVersion sv = SemanticVersion.from(id);
        become(sv);
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
}
