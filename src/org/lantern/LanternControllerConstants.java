package org.lantern;

import java.util.HashMap;
import java.util.Map;

/**
 * Constants for Lantern.
 */
public class LanternControllerConstants {

    static final String MANDRILL_API_KEY = "secret"; // keep this secret
    static final String MANDRILL_API_BASE_URL = "https://mandrillapp.com/api/1.0/";
    static final String MANDRILL_API_SEND_TEMPLATE_URL = MANDRILL_API_BASE_URL + "messages/send-template.json";
    static final String INVITE_EMAIL_TEMPLATE_NAME = "invite-notification";
    static final String INVITE_EMAIL_SUBJECT = "You have been invited to Lantern";
    static final String INVITE_EMAIL_FROM_ADDRESS = "beta@getlantern.org";
    static final String INVITE_EMAIL_FROM_NAME = "Lantern Beta";
    static final String INVITE_EMAIL_BCC_ADDRESS = "bcc@getlantern.org";

    // query string param to bypass password wall on getlantern.org:
    // XXX handle this better? it's duplicated in getlantern.org code's
    // secrets.py, and it'd be nice to be able to change its value without
    // having to redeploy Lantern Controller
    static final String ACCESSKEY = "secret";

    // XXX these are out of date?:
    public static final String UPDATE_URLS_KEY = "urls";
    public static final String UPDATE_RELEASE_DATE = "2012-01-31T11:15:00Z";
    
    public static final Map<String, String> UPDATE_URLS = 
        new HashMap<String, String>();
    
    public static final double LATEST_VERSION = 0.20;
    
    private static final String URL_BASE = 
        "http://cdn.getlantern.org/Lantern-"+LATEST_VERSION;
    
    static {
        UPDATE_URLS.put("macos", URL_BASE+".exe");
        UPDATE_URLS.put("windows", URL_BASE+".dmg");
        UPDATE_URLS.put("ubuntu", URL_BASE+".deb");
        UPDATE_URLS.put("fedora", URL_BASE+".rpm");
        UPDATE_URLS.put("tarball", URL_BASE+".tgz");
    }

    static final String FALLBACK_INSTALLER_HOST = "http://s3.amazonaws.com";
    static final String INSTALLER_BASE_URL = "/lantern/" + LATEST_VERSION;
    static final String INSTALLER_URL_DMG = INSTALLER_BASE_URL + ".dmg";
    static final String INSTALLER_URL_EXE = INSTALLER_BASE_URL + ".exe";
    static final String INSTALLER_URL_DEB32 = INSTALLER_BASE_URL + "-32-bit.deb";
    static final String INSTALLER_URL_DEB64 = INSTALLER_BASE_URL + "-64-bit.deb";

    
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
}
