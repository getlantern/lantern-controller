package org.lantern.data;

public class InstallerUrls {
    private String osx;
    private String windows;
    private String ubuntu32;
    private String ubuntu64;

    public InstallerUrls() {
    }

    public InstallerUrls(String osx, String windows, String ubuntu32,
            String ubuntu64) {
        this.osx = osx;
        this.windows = windows;
        this.ubuntu32 = ubuntu32;
        this.ubuntu64 = ubuntu64;
    }

    public String getOsx() {
        return osx;
    }

    public void setOsx(String osx) {
        this.osx = osx;
    }

    public String getWindows() {
        return windows;
    }

    public void setWindows(String windows) {
        this.windows = windows;
    }

    public String getUbuntu32() {
        return ubuntu32;
    }

    public void setUbuntu32(String ubuntu32) {
        this.ubuntu32 = ubuntu32;
    }

    public String getUbuntu64() {
        return ubuntu64;
    }

    public void setUbuntu64(String ubuntu64) {
        this.ubuntu64 = ubuntu64;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().equals("");
    }

    public InstallerUrls merge(InstallerUrls other) {
        return new InstallerUrls(isBlank(other.osx) ? osx : other.osx,
                isBlank(other.windows) ? windows : other.windows,
                isBlank(other.ubuntu32) ? ubuntu32 : other.ubuntu32,
                isBlank(other.ubuntu64) ? ubuntu64 : other.ubuntu64);
    }
}