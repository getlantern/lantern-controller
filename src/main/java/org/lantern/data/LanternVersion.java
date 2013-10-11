package org.lantern.data;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.persistence.Embedded;
import javax.persistence.Id;

import org.lantern.OS;

public class LanternVersion {
    private static final long serialVersionUID = 1953109001251375722L;

    private final static transient Logger LOG = Logger.getLogger("LanternVersion");

    @Id
    @Embedded
    private Version version;

    private int gitSha;

    private Date releaseDate;

    private Map <OS,String> installerUrls = new HashMap<OS,String>();

    private String infoUrl;

    public LanternVersion(Version version, int gitSha, Date releaseDate,
            Map<OS, String> installerUrls, String infoUrl) {
        this.version = version;
        this.gitSha = gitSha;
        this.releaseDate = releaseDate;
        this.installerUrls = installerUrls;
        this.infoUrl = infoUrl;
    }

    public Version getVersion() {
        return version;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    public int getGitSha() {
        return gitSha;
    }

    public void setGitSha(int gitSha) {
        this.gitSha = gitSha;
    }

    public Date getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(Date releaseDate) {
        this.releaseDate = releaseDate;
    }

    public Map<OS, String> getInstallerUrls() {
        return installerUrls;
    }

    public void setInstallerUrls(Map<OS, String> installerUrls) {
        this.installerUrls = installerUrls;
    }

    public String getInfoUrl() {
        return infoUrl;
    }

    public void setInfoUrl(String infoUrl) {
        this.infoUrl = infoUrl;
    }
}
