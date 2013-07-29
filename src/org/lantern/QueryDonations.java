package org.lantern;

import java.io.InputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

//import org.lantern.data.Dao;

@SuppressWarnings("serial")
public class QueryDonations extends HttpServlet {

    private static final String queryUrl =
        "https://rally.org/api/causes/iD1Ibm17EAA/donations?access_token="
        + LanternControllerConstants.getRallyAccessToken();

    private static final transient Logger log = Logger
            .getLogger(QueryDonations.class.getName());

    private static final int DONATIONS_PER_PAGE = 25;

    // How many pages will we fetch and accumulate into a Task Queue task.
    // To save tasks and requests.
    private static final int PAGES_PER_BATCH = 5;

    @Override
    public void doGet(final HttpServletRequest request,
                      final HttpServletResponse response) {
        StringBuilder s = new StringBuilder();
        List<Map<String, Object>> l = parseJsonDonations(getDonationsInputStream());
        for (Map<String, Object> donation : l) {
            s.append("id: ");
            s.append(donation.get("id").toString());
            s.append("\n");
            s.append("email: ");
            s.append(donation.get("email").toString());
            s.append("\n");
            s.append("amount_cents: ");
            s.append(Integer.toString((Integer)donation.get("amount_cents")));
            s.append("\n");
        }
        LanternControllerUtils.populateOKResponse(response, s.toString());
    }

    private InputStream getDonationsInputStream() {
        try {
            return new URL(queryUrl).openStream();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Map<String, Object>> parseJsonDonations(InputStream is) {
        ObjectMapper om = new ObjectMapper();
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ret = om.readValue(is, List.class);
            return ret;
        } catch (final UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        } catch (final JsonParseException e) {
            log.severe("Error parsing client message: " + e.getMessage());
            throw new RuntimeException(e);
        } catch (final JsonMappingException e) {
            log.severe("Error parsing client message: " + e.getMessage());
            throw new RuntimeException(e);
        } catch (IOException e) {
            log.severe("Error reading client message: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
