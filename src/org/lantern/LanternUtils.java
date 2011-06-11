package org.lantern;

import java.util.Collection;

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
}


