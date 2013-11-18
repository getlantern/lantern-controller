package org.lantern.data;

import java.util.Date;
import java.util.logging.Logger;

import javax.persistence.Id;

import org.lantern.SemanticVersion;


public class LanternVersion {
    private final static transient Logger LOG = Logger
            .getLogger("LanternVersion");

    public final static String SINGLETON_KEY = "latest";

    /**
     * We use the "id" field for the version string, but we don't key
     * by it in the datastore since this is a singleton.
     */
    @Id
    private String key;

    private String id;

    private String gitSha;

    private Date releaseDate;

    private String infoUrl;

    public LanternVersion() {
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public LanternVersion(String id, String gitSha, Date releaseDate, String infoUrl) {
        this.key = SINGLETON_KEY;
        this.id = id;
        this.gitSha = gitSha;
        this.releaseDate = releaseDate;
        this.infoUrl = infoUrl;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public SemanticVersion getSemanticVersion() {
        return SemanticVersion.from(id);
    }

    public void setSemanticVersion(SemanticVersion semanticVersion) {
        id = semanticVersion.toString();
    }

    public String getGitSha() {
        return gitSha;
    }

    public void setGitSha(String gitSha) {
        this.gitSha = gitSha;
    }

    public Date getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(Date releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getInfoUrl() {
        return infoUrl;
    }

    public void setInfoUrl(String infoUrl) {
        this.infoUrl = infoUrl;
    }
}