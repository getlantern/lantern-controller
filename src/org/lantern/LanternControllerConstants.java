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
    static final String INVITE_EMAIL_TEMPLATE_NAME = "cloud-invite";
    static final String INVITE_EMAIL_SUBJECT = "Lantern Invitation";
    static final String INVITE_EMAIL_FROM_ADDRESS = "invite@getlantern.org";
    static final String INVITE_EMAIL_FROM_NAME = "Lantern Beta";
    static final String INVITE_EMAIL_BCC_ADDRESS = "invite@getlantern.org";

    private static String mandrillApiKey;

    public final static VersionNumber LATEST_VERSION = new VersionNumber("0.21.0");

    static {
        try {
            PropertiesConfiguration config = new PropertiesConfiguration(LanternControllerConstants.class.getResource("secrets"));
            mandrillApiKey = config.getString("mandrillApiKey");
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public static final long UPDATE_TIME_MILLIS = 60 * 1000;

    public static final int MAX_USERS = 100;
    public static final String ADMIN_EMAIL = "admin@getlantern.org";
    public static final String NOTIFY_ON_MAX_USERS = "admin@getlantern.org";

    public static String getMandrillApiKey() {
        return mandrillApiKey;
    }
}
