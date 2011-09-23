package org.lantern;

/**
 * Constants for Lantern.
 */
public class LanternConstants {
    
    /**
     * The key for the version of Lantern the client is recording.
     */
    public static final String VERSION_KEY = "v";
    
    public static final double LATEST_VERSION = 0.1;
    
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

    public static final String UPDATE_MESSAGE_KEY = "upm";
    
    public static final String UPDATE_MESSAGE = 
        "Lantern "+LATEST_VERSION+" is now available. To update please visit " +
        "the <br> <a href='http://www.getlantern.org'>Lantern download page</a>.";
    
    /**
     * The key for the update JSON object.
     */
    public static final String UPDATE_KEY = "uk";

    public static final String UPDATE_VERSION_KEY = "uv";
    
    public static final String UPDATE_TITLE_KEY = "upt";

    public static final String UPDATE_TITLE = "Update Lantern?";

    public static final String UPDATE_URL_KEY = "uuk";

    public static final String UPDATE_URL = "http://www.getlantern.org";
}
