package org.lantern;

import java.util.HashMap;
import java.util.Map;

/**
 * Constants for Lantern.
 */
public class LanternConstants {
    

    public static final String UPDATE_RELEASED_KEY = "released";
    public static final String UPDATE_URLS_KEY = "urls";
    public static final String UPDATE_RELEASE_DATE = "2012-01-31T11:15:00Z";
    
    public static final Map<String, String> UPDATE_URLS = 
        new HashMap<String, String>();
    
    public static final double LATEST_VERSION = 0.96;
    
    private static final String URL_BASE = 
        "http://cdn.getlantern.org/Lantern-"+LATEST_VERSION;
    
    static {
        UPDATE_URLS.put("macos", URL_BASE+".exe");
        UPDATE_URLS.put("windows", URL_BASE+".dmg");
        UPDATE_URLS.put("ubuntu", URL_BASE+".deb");
        UPDATE_URLS.put("fedora", URL_BASE+".rpm");
        UPDATE_URLS.put("tarball", URL_BASE+".tgz");
    }
    
    /**
     * This is the local proxy port data is relayed to on the "server" side
     * of P2P connections.
     */
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
    
    public static final long UPDATE_TIME_MILLIS = 60 * 1000;

    public static final String UPDATE_MESSAGE_KEY = "message";
    
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
