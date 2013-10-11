package org.lantern.data;

public class Version {
    private String major;
    private String minor;
    private String patch;
    private String tag;

    public Version(String major, String minor, String patch, String tag) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.tag = tag;
    }

    public String getMajor() {
        return major;
    }

    public String getMinor() {
        return minor;
    }

    public String getPatch() {
        return patch;
    }

    public String getTag() {
        return tag;
    }
}
