package org.lantern.data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

public class SemanticVersion implements Comparable<SemanticVersion> {
    private static final String FORMAT = "(\\d+)\\.(\\d+)\\.(\\d+)(\\-\\w+)?";
    private static final Pattern PATTERN = Pattern.compile("^" + FORMAT + "$");

    private int major;
    private int minor;
    private int patch;
    private String tag;

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
        if (!StringUtils.isEmpty(tag)) {
            tag = tag.substring(1); // strip the "-"
        }
        return new SemanticVersion(major, minor, patch, tag);
    }

    /**
     * @param tag Pass null to indicate no tag.
     */
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
        String s = String.format("%1$s.%2$s.%3$s", major, minor, patch);
        return s + (StringUtils.isEmpty(tag) ? "" : ("-"+tag));
    }

    private String toComparableString() {
        String s = String.format("%03d.%03d.%03d", major, minor, patch);
        return s + (StringUtils.isEmpty(tag) ? "." : ("-"+tag)); // use '.' for final versions since it's > '-'
    }

    @Override
    public int compareTo(SemanticVersion o) {
        if (this.equals(o)) {
            return 0;
        }
        return this.toComparableString().compareTo(o.toComparableString());
    }


    // TODO: move this to unit tests
    public static void main(String[] args) {
        SemanticVersion beta7 = new SemanticVersion(1, 0, 0, "beta7");
        SemanticVersion rc1 = new SemanticVersion(1, 0, 0, "rc1");
        SemanticVersion final_ = new SemanticVersion(1, 0, 0, null);
        System.out.println(beta7.toComparableString());
        System.out.println(rc1.toComparableString());
        System.out.println(final_.toComparableString());
        System.out.println(beta7.compareTo(rc1) < 0);
        System.out.println(rc1.compareTo(final_) < 0);
        System.out.println(beta7.compareTo(final_) < 0);
    }
}