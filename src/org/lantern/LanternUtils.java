package org.lantern;

import java.util.ArrayList;
import java.util.Collection;

import com.google.appengine.api.xmpp.Message;
import com.google.appengine.repackaged.org.json.JSONArray;
import com.google.appengine.repackaged.org.json.JSONException;
import com.google.common.collect.Sets;

/**
 * Utility methods for use with Lantern.
 */
public class LanternUtils {
    
    /**
     * Censored country codes, in order of population.
     */
    private static final Collection<String> CENSORED =
        Sets.newHashSet(
            "CN",
            "IN",
            "PK",
            "RU",
            "VN",
            "EG",
            "ET",
            "IR",
            "TH",
            "MM",
            "KR",
            "UA",
            "SD",
            "DZ",
            "MA",
            "AF",
            "UZ",
            "SA",
            "YE",
            "SY",
            "KZ",
            "TN",
            "BY",
            "AZ",
            "LY",
            "OM");

    // These country codes have US export restrictions, and therefore cannot
    // access App Engine sites.
    private static final Collection<String> EXPORT_RESTRICTED =
        Sets.newHashSet(
            "SY");


    public static boolean isCensored(final String countryCode) {
        return CENSORED.contains(countryCode);
    }


    public static Collection<String> toCollection(final JSONArray json) {
        final int length = json.length();
        final Collection<String> strs = new ArrayList<String>(length);
        for (int i = 0; i < length; i++) {
            try {
                strs.add((String) json.get(i));
            } catch (final JSONException e) {
                e.printStackTrace();
            }
        }
        return strs;
    }


    public static String userId(final Message message) {
        return message.getFromJid().getId().split("/")[0];
    }
    
    public static String instanceId(final Message message) {
        return message.getFromJid().getId().split("/")[1];
    }

    public static String jidToUserId(final String fullId) {
        return fullId.split("/")[0];
    }
    
    public static String jidToInstanceId(final String fullId) {
        return fullId.split("/")[1];
    }
}


