package org.lantern.data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import javax.jdo.annotations.IdentityType;
import javax.jdo.annotations.NotPersistent;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

import org.apache.commons.lang3.StringUtils;

import com.google.appengine.api.datastore.Text;

@PersistenceCapable(identityType = IdentityType.APPLICATION)
public class Settings implements Serializable {
    static {
        Settings.class.getClassLoader().setDefaultAssertionStatus(true);
    }

    private static final long serialVersionUID = 8363339538958438691L;

    public static final String singletonKey = "settings";

    private static final transient Logger log =
        Logger.getLogger(Settings.class.getName());

    @PrimaryKey
    @Persistent
    private String key;

    @NotPersistent
    private HashMap<String, String> settings = new HashMap<String, String>();

    @Persistent
    private Text persistedSettings;

    //in epoch seconds
    @Persistent
    private long lastUpdated;

    public Settings() {
        this(singletonKey);
    }

    public Settings(final String key) {
        this.key = key;
    }

    public String getSettings(final String name) {
        return settings.get(name);
    }

    /**
     * Returns a copy of the complete settings map
     * @return
     */
    public Map<String, String> getAllSettings() {
        return new HashMap<String, String>(settings);
    }

    public void set(final String name, final String value) {
        if (name.contains(":") || name.contains(",")) {
            throw new RuntimeException("Setting names must not contain : or ,");
        }
        settings.put(name, value);
    }

    public String get(final String name) {
        return settings.get(name);
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(final long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public void prepareForPersistence() {
        StringBuilder sb = new StringBuilder(50000);
        for (Map.Entry<String, String> entry : settings.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            sb.append(name);
            sb.append(":");
            value = value.replace("\\", "\\\\");
            value = value.replace(":", "\\n");
            value = value.replace(",", "\\a");
            sb.append(value);
            sb.append(",");
        }
        String s = sb.toString();
        persistedSettings = new Text(s);
        log.info("Persisted string takes " + s.length() + " characters.");
    }

    public void restore() {
        if (persistedSettings == null) {
            log.info("No settings to restore.");
            return;
        }
        StringTokenizer st = new StringTokenizer(persistedSettings.getValue(),
                                                 ",");
        HashMap<String, String> c = new HashMap<String, String>();

        while (st.hasMoreTokens()) {
            String s = st.nextToken();
            if (s.length() == 0) {
                continue;
            }
            new StringTokenizer(s, ":");
            String[] parts = StringUtils.split(s, ":");
            assert parts.length == 2;
            String name = parts[0];
            String value = parts[1];
            value = value.replace("\\n", ":");
            value = value.replace("\\a", ",");
            value = value.replace("\\\\", "\\");
            c.put(name, value);
        }
        settings = c;
        log.info("Got " + c.size() + " settings.");
    }
}
