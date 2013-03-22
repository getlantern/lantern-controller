package org.lantern;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

/**
 * Constants for Lantern.
 */
public class LanternControllerConstants {

    static final String MANDRILL_API_BASE_URL = "https://mandrillapp.com/api/1.0/";
    static final String MANDRILL_API_SEND_TEMPLATE_URL = MANDRILL_API_BASE_URL + "messages/send-template.json";
    static final String INVITE_EMAIL_TEMPLATE_NAME = "invite-notification";
    static final String INVITE_EMAIL_SUBJECT = "Lantern Invitation";
    static final String INVITE_EMAIL_FROM_ADDRESS = "invite@getlantern.org";
    static final String INVITE_EMAIL_FROM_NAME = "Lantern Beta";
    static final String INVITE_EMAIL_BCC_ADDRESS = "invite@getlantern.org";

    // query string param to bypass password wall on getlantern.org:
    // XXX handle this better? it's duplicated in getlantern.org code's
    // secrets.py, and it'd be nice to be able to change its value without
    // having to redeploy Lantern Controller
    private static String accessKey;
    private static String mandrillApiKey;

    // XXX these are out of date?:
    public static final String UPDATE_URLS_KEY = "urls";
    public static final String UPDATE_RELEASE_DATE = "2012-01-31T11:15:00Z";

    public static final Map<String, String> UPDATE_URLS =
        new HashMap<String, String>();

    public final static VersionNumber LATEST_VERSION = new VersionNumber("0.20");

    private static final String URL_BASE =
        "http://cdn.getlantern.org/Lantern-"+LATEST_VERSION;

    static {
        UPDATE_URLS.put("macos", URL_BASE+".exe");
        UPDATE_URLS.put("windows", URL_BASE+".dmg");
        UPDATE_URLS.put("ubuntu", URL_BASE+".deb");
        UPDATE_URLS.put("fedora", URL_BASE+".rpm");
        UPDATE_URLS.put("tarball", URL_BASE+".tgz");

        try {
            PropertiesConfiguration config = new PropertiesConfiguration(LanternControllerConstants.class.getResource("secrets"));
            accessKey = config.getString("accessKey");
            mandrillApiKey = config.getString("mandrillApiKey");
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }


    // XXX dynamically generate random s3 bucket for this:
    static final String INSTALLER_BASE_URL = "http://s3.amazonaws.com/lantern/latest.";
    static final String INSTALLER_URL_DMG = INSTALLER_BASE_URL + "dmg";
    static final String INSTALLER_URL_EXE = INSTALLER_BASE_URL + "exe";
    static final String INSTALLER_URL_DEB = INSTALLER_BASE_URL + "deb";


    /**
     * This is the local proxy port data is relayed to on the "server" side
     * of P2P connections.
     */
    /*
    public static final int PLAINTEXT_LOCALHOST_PROXY_PORT = 7777;
    public static final int LANTERN_LOCALHOST_HTTP_PORT = 8787;

    public static final int LANTERN_LOCALHOST_HTTPS_PORT = 8788;

    public static final String USER_NAME = "un";
    public static final String PASSWORD = "pwd";

    public static final String DIRECT_BYTES = "db";
    public static final String BYTES_PROXIED = "bp";

    public static final String REQUESTS_PROXIED = "rp";
    public static final String DIRECT_REQUESTS = "dr";

    public static final String MACHINE_ID = "m";
    public static final String COUNTRY_CODE = "cc";
    public static final String WHITELIST_ADDITIONS = "wa";
    public static final String WHITELIST_REMOVALS = "wr";
    public static final String SERVERS = "s";


    public static final String UPDATE_TIME = "ut";
    */

    public static final long UPDATE_TIME_MILLIS = 60 * 1000;

    //public static final String UPDATE_MESSAGE_KEY = "message";

    public static final String UPDATE_MESSAGE =
        "Lantern "+LATEST_VERSION+" is now available with many new performance and usability improvements.";

    public static final String VERSION_KEY = "v";

    /**
     * The key for the update JSON object.
     */
    public static final String UPDATE_KEY = "uk";

    public static final String UPDATE_VERSION_KEY = "number";

    //public static final String UPDATE_TITLE_KEY = "upt";

    //public static final String UPDATE_TITLE = "Update Lantern?";

    public static final String UPDATE_URL_KEY = "url";

    public static final String UPDATE_URL = "http://www.getlantern.org";

    public static final int MAX_USERS = 100;
    public static final String ADMIN_EMAIL = "admin@getlantern.org";
    public static final String NOTIFY_ON_MAX_USERS = "admin@getlantern.org";

    public static String getMandrillApiKey() {
        return mandrillApiKey;
    }

    public static String getAccessKey() {
        return accessKey;
    }
}
