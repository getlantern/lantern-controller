package org.lantern.data;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.persistence.Embedded;
import javax.persistence.Id;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.lantern.OS;
import org.lantern.VersionNumber;

import com.googlecode.objectify.annotation.Serialized;

/**
 * TODO: reduce duplication with Lantern's {@link VersionNumber}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LanternVersion {
    private final static transient Logger LOG = Logger
            .getLogger("LanternVersion");

    @Id
    private String id;

    private String gitSha;

    private Date releaseDate;

    @Embedded
    private InstallerUrls installerUrls = new InstallerUrls();

    private String infoUrl;

    public LanternVersion() {
    }

    public LanternVersion(String id, String gitSha, Date releaseDate,
            InstallerUrls installerUrls, String infoUrl) {
        this.id = id;
        this.gitSha = gitSha;
        this.releaseDate = releaseDate;
        this.installerUrls = installerUrls;
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

    public InstallerUrls getInstallerUrls() {
        return installerUrls;
    }

    public void setInstallerUrls(InstallerUrls installerUrls) {
        this.installerUrls = installerUrls;
    }

    public String getInfoUrl() {
        return infoUrl;
    }

    public void setInfoUrl(String infoUrl) {
        this.infoUrl = infoUrl;
    }

    /**
     * Return a new LanternVersion with installerUrls merged between this
     * instance and other
     * 
     * @param other
     * @return
     */
    public LanternVersion merge(LanternVersion other) {
        InstallerUrls mergedInstallerUrls = installerUrls.merge(other.getInstallerUrls());
        return new LanternVersion(other.getId(), other.getGitSha(),
                other.getReleaseDate(), mergedInstallerUrls, other.getInfoUrl());
    }
}
