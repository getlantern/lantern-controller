package org.lantern;

import java.util.Collection;

import org.apache.commons.lang3.StringUtils;

import com.google.common.collect.Sets;

public class CensoredUtils {

    /**
     * Censored country codes", "in order of population.
     */
    private static final Collection<String> CENSORED =
        Sets.newHashSet(
            // Asia
                /*
            "CN",
            "VN",
            "MM",
            //Mideast:
            "IR",
            "BH",
            "YE",
            "SA",
            "SY",
            //Eurasia:
            "UZ",
            "TM",
            //Africa:
            "ET",
            "ER",
            // LAC:
            "CU"
            */
            // These are taken from ONI data -- 11/16 - any country containing
            // any type of censorship considered "substantial" or "pervasive".
            "AE", "AM", "BH", "CN", "CU", "ET", "ID", "IR", "KP", "KR",
            "KW", "MM", "OM", "PK", "PS", "QA", "SA", "SD", "SY", "TM", "UZ",
            "VN", "YE"
            );


    public static boolean isCensored(final String countryCode) {
        if (StringUtils.isBlank(countryCode)) {
            return true;
        }
        return CENSORED.contains(countryCode.toUpperCase().trim());
    }
}
