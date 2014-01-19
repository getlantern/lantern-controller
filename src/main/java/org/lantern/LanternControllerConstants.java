package org.lantern;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

import com.google.appengine.api.utils.SystemProperty;

import com.amazonaws.auth.BasicAWSCredentials;

/**
 * Constants for Lantern.
 */
public class LanternControllerConstants {

    static final String MANDRILL_API_BASE_URL = "https://mandrillapp.com/api/1.0/";
    static final String MANDRILL_API_SEND_URL = MANDRILL_API_BASE_URL + "messages/send.json";
    static final String INVITE_EMAIL_FROM_ADDRESS = "invite@getlantern.org";
    static final String INVITE_EMAIL_FROM_SUFFIX = " via Lantern";
    static final String INVITE_EMAIL_BCC_ADDRESS = "lantern-invites@googlegroups.com";
    public static final String DEFAULT_FALLBACK_HOST_AND_PORT;
    public static final String FALLBACK_PROXY_LAUNCHING = "launching";
    public static final boolean IS_RUNNING_IN_SANDBOX = "Development".equals(SystemProperty.environment.key());

    /**
     * The inviter for all invites triggered by an email request.
     */
    public static final String EMAIL_REQUEST_INVITER
        = "invite@getlantern.org";

    private static String mandrillApiKey;
    private static String mailChimpApiKey;

    public static final BasicAWSCredentials AWS_CREDENTIALS;
    public static final String AWS_REGION = "ap-southeast-1";

    public static final String CONTROLLER_ID = SystemProperty.applicationId.get();
    public static final String BASE_URL
        = String.format("https://%1$s.appspot.com", CONTROLLER_ID);


    static {
        try {
            PropertiesConfiguration config
                = new PropertiesConfiguration(
                        LanternControllerConstants.class.getResource("secrets"));
            mailChimpApiKey = config.getString("mailchimpApiKey");
            mandrillApiKey = config.getString("mandrillApiKey");
            DEFAULT_FALLBACK_HOST_AND_PORT
                = config.getString("defaultFallbackHostAndPort");
            AWS_CREDENTIALS = new BasicAWSCredentials(config.getString("awsAccessKeyId"),
                                                      config.getString("awsSecretKey"));
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public static final long UPDATE_TIME_MILLIS = 5 * 60 * 1000;

    public static final int MAX_USERS = 100;
    public static final String TEAM_EMAIL = "team@getlantern.org";
    public static final String ADMIN_EMAIL = "admin@getlantern.org";
    public static final String NOTIFY_ON_MAX_USERS = "admin@getlantern.org";
    public static final String MAILCHIMP_LIST_ID = "cdc1af284e";
    public static final String MAILCHIP_API_URL_BASE = "http://<dc>.api.mailchimp.com/1.3/?method=<method>";
    public static final int DEFAULT_MAX_FRIENDS = 5;
    public static final int MIN_MAX_FRIENDS = 1;
    
    public static String getMandrillApiKey() {
        return mandrillApiKey;
    }
    public static String getMailChimpApiKey() {
        return mailChimpApiKey;
    }
}
