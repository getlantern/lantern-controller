package org.lantern;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;

/**
 * Constants for Lantern.
 */
public class LanternControllerConstants {

    static final String MANDRILL_API_BASE_URL = "https://mandrillapp.com/api/1.0/";
    static final String MANDRILL_API_SEND_TEMPLATE_URL = MANDRILL_API_BASE_URL + "messages/send-template.json";
    static final String INVITE_EMAIL_TEMPLATE_NAME = "invite-wrappers";
    static final String INVITE_EMAIL_SUBJECT = "Lantern Invitation";
    static final String INVITE_EMAIL_FROM_ADDRESS = "invite@getlantern.org";
    static final String INVITE_EMAIL_FROM_NAME = "Lantern Beta";
    static final String INVITE_EMAIL_BCC_ADDRESS = "invite@getlantern.org";

    private static String mandrillApiKey;
    private static String awsAccessKeyId;
    private static String awsSecretKey;
    private static String mailChimpApiKey;
    private static String rallyAccessToken;


    static {
        try {
            PropertiesConfiguration config = new PropertiesConfiguration(LanternControllerConstants.class.getResource("secrets"));
            mailChimpApiKey = config.getString("mailchimpApiKey");
            mandrillApiKey = config.getString("mandrillApiKey");
            awsAccessKeyId = config.getString("awsAccessKeyId");
            awsSecretKey = config.getString("awsSecretKey");
            rallyAccessToken = config.getString("rallyAccessToken");
        } catch (ConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public static final long UPDATE_TIME_MILLIS = 60 * 1000;

    public static final int MAX_USERS = 100;
    public static final String ADMIN_EMAIL = "admin@getlantern.org";
    public static final String NOTIFY_ON_MAX_USERS = "admin@getlantern.org";
    public static final String MAILCHIMP_LIST_ID = "cdc1af284e";
    public static final String MAILCHIP_API_URL_BASE = "http://<dc>.api.mailchimp.com/1.3/?method=<method>";

    public static final String DONATION_ID_KEY = "id";
    public static final String DONATION_EMAIL_KEY = "email";
    public static final String DONATION_AMOUNT_KEY = "amount_cents";

    public static String getMandrillApiKey() {
        return mandrillApiKey;
    }
    public static String getAWSAccessKeyId() {
        return awsAccessKeyId;
    }
    public static String getAWSSecretKey() {
        return awsSecretKey;
    }
    public static String getMailChimpApiKey() {
        return mailChimpApiKey;
    }
    public static String getRallyAccessToken() {
        return rallyAccessToken;
    }
}
