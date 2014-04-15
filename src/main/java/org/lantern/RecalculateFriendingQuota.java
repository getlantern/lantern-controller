package org.lantern;

import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.lantern.data.Dao;
import org.lantern.data.Dao.DbCall;
import org.lantern.data.LanternUser;
import org.lantern.friending.Friending;
import org.lantern.loggly.LoggerFactory;

import com.googlecode.objectify.Objectify;

/**
 * This task recalculates a user's friending quota based on their current
 * degree and the current formula for determining max friends.  If the user
 * already has a higher quota than what is determined by the formula, the
 * existing quota is left alone.
 */
@SuppressWarnings("serial")
public class RecalculateFriendingQuota extends HttpServlet {

    private static final transient Logger LOGGER = LoggerFactory
            .getLogger(RecalculateFriendingQuota.class);
    
    public static final String USER_ID = "userId";
    public static final String DEGREE_DELTA = "degreeDelta";

    @Override
    public void doPost(final HttpServletRequest request,
            final HttpServletResponse response) throws ServletException {
        final String userId = request.getParameter(USER_ID);
        LOGGER.info("Recalculating friending quota for user: " + userId);
        Dao dao = new Dao();
        dao.withTransaction(new DbCall<Void>() {
            @Override
            public Void call(Objectify ofy) {
                LanternUser user = ofy.find(LanternUser.class, userId);
                Friending.recalculateQuota(ofy, userId, user.getDegree());
                return null;
            }
        });
    }

}
