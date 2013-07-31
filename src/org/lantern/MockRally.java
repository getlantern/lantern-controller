// THIS IS TEST CODE. MEH! :)

package org.lantern;

import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


@SuppressWarnings("serial")
public class MockRally extends HttpServlet {

    private static final transient Logger log = Logger
            .getLogger(MockRally.class.getName());

    private String[][] donations = {
        // id, email, amount, date.
        {"abcdef01", "lanternuser@gmail.com", "500", "2012-05-03 09:57:12"},
        {"abcdef02", "lanternuser@gmail.com", "256", "2012-05-03 09:57:12"},
        {"abcdef03", "lanternuser@gmail.com", "600", "2012-05-03 09:57:12"},
        {"abcdef04", "lanternuser@gmail.com", "500", "2012-05-03 09:57:12"},
        {"abcdef05", "lanternuser@gmail.com", "400", "2012-05-03 09:57:12"},
        {"abcdef06", "lanternuser@gmail.com", "500", "2012-05-03 09:57:12"},
        {"abcdef07", "lanternuser@gmail.com", "500", "2012-05-04 09:57:12"},
        {"abcdef08", "lanternuser@gmail.com", "500", "2012-05-04 09:57:12"},
        {"abcdef09", "lanternuser@gmail.com", "500", "2012-05-04 09:57:12"},
        {"abcdef10", "lanternuser@gmail.com", "500", "2012-05-04 09:57:12"},
        {"abcdef11", "lanternuser@gmail.com", "500", "2012-05-04 09:57:12"},
        {"abcdef12", "lanternuser@gmail.com", "500", "2012-05-04 09:57:12"},
        {"abcdef13", "lanternuser@gmail.com", "500", "2012-05-04 09:57:12"},
        {"abcdef14", "lanternuser@gmail.com", "500", "2012-05-04 09:57:12"},
        {"abcdef15", "lanternuser@gmail.com", "500", "2012-05-04 09:57:12"},
        {"abcdef16", "lanternuser@gmail.com", "500", "2012-06-03 09:57:12"},
        {"abcdef17", "lanternuser@gmail.com", "500", "2012-06-03 09:57:12"},
        {"abcdef18", "lanternuser@gmail.com", "500", "2012-06-03 09:57:12"},
        {"abcdef19", "lanternuser@gmail.com", "500", "2012-06-03 09:57:12"},
        {"abcdef20", "lanternuser@gmail.com", "500", "2012-06-03 09:57:12"},
        {"abcdef21", "lanternuser@gmail.com", "500", "2012-06-03 09:57:12"},
        {"abcdef22", "lanternuser@gmail.com", "500", "2012-06-03 09:57:12"},
        {"abcdef23", "lanternuser@gmail.com", "500", "2013-05-03 09:57:12"},
        {"abcdef24", "lanternuser@gmail.com", "500", "2013-05-03 09:57:12"},
        {"abcdef25", "lanternuser@gmail.com", "500", "2013-05-03 09:57:12"},
        {"abcdef26", "lanternuser@gmail.com", "500", "2013-05-03 09:57:12"},
        {"abcdef27", "lanternuser@gmail.com", "500", "2013-05-03 09:57:12"},
        {"abcdef28", "lanternuser@gmail.com", "500", "2013-05-03 09:57:12"},
        {"abcdef29", "lanternuser@gmail.com", "500", "2013-05-03 09:57:12"},
        {"abcdef30", "lanternuser@gmail.com", "500", "2013-05-03 09:57:12"},
        {"abcdef31", "lanternuser@gmail.com", "500", "2013-05-03 09:57:12"},
        {"abcdef32", "lanternuser@gmail.com", "500", "2013-05-03 09:57:12"},
        {"abcdef33", "lanternuser@gmail.com", "500", "2013-05-03 09:57:12"},
        {"abcdef34", "lanternuser@gmail.com", "500", "2013-05-03 09:57:12"},
        {"abcdef35", "lanternuser@gmail.com", "500", "2013-05-03 09:57:12"},
        {"abcdef36", "lanternuser@gmail.com", "500", "2013-05-03 09:57:12"},
        {"abcdef37", "lanternuser@gmail.com", "500", "2013-05-03 09:57:12"},
        {"abcdef38", "lanternuser@gmail.com", "500", "2013-05-03 09:57:12"},
        {"abcdef39", "lanternuser@gmail.com", "500", "2013-05-03 09:57:12"},
        {"abcdef40", "lanternuser@gmail.com", "500", "2013-05-03 09:57:12"},
    };

    @Override
    public void doGet(final HttpServletRequest request,
                      final HttpServletResponse response) {
        final String idKey = LanternControllerConstants.DONATION_ID_KEY;
        final String emailKey = LanternControllerConstants.DONATION_EMAIL_KEY;
        final String amountKey
            = LanternControllerConstants.DONATION_AMOUNT_KEY;
        String start_date = request.getParameter("start_date");
        if (start_date == null) {
            start_date = "1970-01-01";
        }
        String pageStr = request.getParameter("page");
        // rally.org page indices are 1-based.  Let's normalize it to 0-based.
        int page = pageStr == null ? 0 : Integer.parseInt(pageStr) - 1;
        log.info("start_date: " + start_date + "; page: " + page);

        // First donation not older than the given start_date, or the length of
        // donations if there is none (the latter works out fine as a fallback;
        // we'll return the empty list).
        int fid;
        for (fid=0; fid<donations.length; fid++) {
            if (getDate(donations[fid][3]).compareTo(start_date) >= 0) {
                log.info("First non-ancient donation at " + fid);
                break;
            }
        }
        int start = fid + page * QueryDonations.MAX_DONATIONS_PER_PAGE;
        // Not inclusive.
        int end = Math.min(start + QueryDonations.MAX_DONATIONS_PER_PAGE,
                           donations.length);

        log.info("start: " + start + "; end: " + end);
        StringBuilder s = new StringBuilder();
        s.append("[");
        for (int i=start; i<end; i++) {
            String[] d = donations[i];
            if (i != start) {
                s.append(",");
            }
            s.append("{");
            s.append(keyVal(quote(idKey), quote(d[0])));
            s.append(keyVal(quote(emailKey), quote(d[1])));
            // Amounts come as integers in the rally.org JSON, so FWIW we don't
            // quote them.
            s.append(keyVal(quote(amountKey), d[2]));
            s.append(keyVal(quote("created_at"), quote(d[3])));
            // This is the only other outlier of the rally.org JSON.  We don't
            // use it, but I include it to check we don't choke on it either.
            s.append(quote("custom_field_values"));
            // Close object without commas.
            s.append(":[]}");
        }
        s.append("]");
        LanternControllerUtils.populateOKResponse(response, s.toString());
    }

    private String getDate(String dateTime) {
        return dateTime.split(" ")[0];
    }

    private String quote(String s) {
        return "\"" + s + "\"";
    }

    private String keyVal(String k, String v) {
        return k + ":" + v + ",";
    }
}
