package org.lantern;

import java.util.Collection;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Sets;

public class CensoredUtils {

    /**
     * Censored country codes, in order of population.
     */
    private static final Collection<String> CENSORED =
        Sets.newHashSet(
            // Asia 
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
            "CU");

    
    public static boolean isCensored(final String countryCode) {
        if (StringUtils.isBlank(countryCode)) {
            return true;
        }
        return CENSORED.contains(countryCode.toUpperCase().trim());
    }
}
