package org.lantern.data;

import javax.persistence.Id;


public class InstallerBucket {
    
    @Id
    private String id;

    /**
     * For how many invited servers are installers being hosted in this bucket.
     *
     * Each set of installers (.dmg, .exe, -32.deb, etc.) for a particular
     * inviter server lives in a folder under the bucket root.  Across the
     * codebase, we call these (bucket, folder) pairs "installer locations". 
     */
    private int installerLocations;
    
    public InstallerBucket() {
        super();
    }

    public InstallerBucket(final String id) {
        super();
        this.id = id;
        this.installerLocations = 0;
    }

    public String getId() {
        return id;
    }

    public void setInstallerLocations(int installerLocations) {
        this.installerLocations = installerLocations;
    }

    public int getInstallerLocations() {
        return installerLocations;
    }
}
