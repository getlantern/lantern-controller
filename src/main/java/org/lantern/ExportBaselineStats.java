package org.lantern;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.lantern.data.Dao;
import org.lantern.data.Dao.DbCall;
import org.lantern.data.LanternUser;
import org.lantern.loggly.LoggerFactory;
import org.lantern.monitoring.Stats.Counters;
import org.lantern.monitoring.Stats.Members;
import org.lantern.monitoring.Stats;
import org.lantern.monitoring.StatshubAPI;

import com.googlecode.objectify.Objectify;

/**
 * This task exports baseline stats for the user identified by userid.
 */
@SuppressWarnings("serial")
public class ExportBaselineStats extends HttpServlet {

    private static final transient Logger LOGGER = LoggerFactory
            .getLogger(ExportBaselineStats.class);

    private static final StatshubAPI statshub = new StatshubAPI();

    private static final transient Set<String> CENSORING_COUNTRIES = new HashSet<String>(
            Arrays.asList(new String[] {
                    "CN", // China
                    "VN", // Vietnam
                    "IR", // Iran
                    "CU", // Cuba

                    // "all count":
                    "SY", // Syria
                    "SA", // Saudi Arabia
                    "BH", // Bahrain
                    "ET", // Ethiopia
                    "ER", // Eritrea [1]
                    "UZ", // Uzbekistan
                    "TM", // Turkmenistan
                    "PK", // Pakistan [1]
                    "TR" // Turkey
            }));

    @Override
    public void doPost(final HttpServletRequest request,
            final HttpServletResponse response) throws ServletException {
        final String userId = request.getParameter("userId");
        LOGGER.info("Exporting baseline stats for user: " + userId);
        LanternUser user = userWithGuid(userId);

        String countryToUse = countryForUser(user);
        boolean isCensored = CENSORING_COUNTRIES.contains(countryToUse);
        
        org.lantern.monitoring.Stats stats = new org.lantern.monitoring.Stats();
        if (user.isEverSignedIn()) {
            stats.setMember(Members.userOnlineEver, user.getGuid());
        }
        if (user.getBytesProxied() > 0) {
            if (isCensored) {
                // If the user is censored, we assume that all activity was Get
                stats.setCounter(Counters.bytesGotten, user.getBytesProxied());
            } else {
                // Otherwise we assume that all activity is Give
                stats.setCounter(Counters.bytesGiven, user.getBytesProxied());
            }
        }

        try {
            statshub.postUserStats(user.getGuid() + "_old", countryToUse, stats);
            LOGGER.info("Exported baseline stats for: " + userId);
            LanternControllerUtils.populateOKResponse(response, "OK");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                    "Unable to post to statshub: " + e.getMessage(), e);
            throw new ServletException(e);
        }

    }
    
    public static LanternUser userWithGuid(final String userId) {
        return new Dao().withTransaction(new DbCall<LanternUser>() {
            @Override
            public LanternUser call(Objectify ofy) {
                LanternUser user = ofy.find(LanternUser.class, userId);
                if (user != null && user.initializeGuidIfNecessary()) {
                    ofy.put(user);
                }
                return user;
            }
        });
    }
    
    /**
     * Picks one of the user's countries, prefering censored ones.
     * 
     * @param user
     * @return
     */
    public static String countryForUser(LanternUser user) {
        String countryToUse = Stats.UNKNOWN_COUNTRY;
        for (String country : user.getCountryCodes()) {
            countryToUse = "--".equals(country) ? countryToUse : country;
            if (CENSORING_COUNTRIES.contains(countryToUse.toUpperCase())) {
                break;
            }
        }
        return countryToUse;
    }

}
