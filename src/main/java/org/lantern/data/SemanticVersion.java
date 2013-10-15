package org.lantern.data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SemanticVersion {
    private static final String FORMAT = "(\\d+)\\.(\\d+)\\.(\\d+)\\-(\\w+)";
    private static final Pattern PATTERN = Pattern.compile("^" + FORMAT + "$");

    private int major;
    private int minor;
    private int patch;
    private String tag;

    /**
     * TODO: add unit tests
     * @param s
     * @return
     */
    public SemanticVersion() {
    }

    public static SemanticVersion from(String s) {
        Matcher matcher = PATTERN.matcher(s);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(String.format("\"%1$s\" does not match pattern %2$s", s, FORMAT));
        }
        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));
        String tag = matcher.group(4);
        return new SemanticVersion(major, minor, patch, tag);
    }

    public SemanticVersion(int major, int minor, int patch, String tag) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.tag = tag;
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getPatch() {
        return patch;
    }

    public String getTag() {
        return tag;
    }

    public void setMajor(int major) {
        this.major = major;
    }

    public void setMinor(int minor) {
        this.minor = minor;
    }

    public void setPatch(int patch) {
        this.patch = patch;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + major;
        result = prime * result + minor;
        result = prime * result + patch;
        result = prime * result + ((tag == null) ? 0 : tag.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SemanticVersion other = (SemanticVersion) obj;
        if (major != other.major)
            return false;
        if (minor != other.minor)
            return false;
        if (patch != other.patch)
            return false;
        if (tag == null) {
            if (other.tag != null)
                return false;
        } else if (!tag.equals(other.tag))
            return false;
        return true;
    }

    public String toString() {
        return String.format("%1$s.%2$s.%3$s-%4$s", major, minor, patch, tag);
    }
}