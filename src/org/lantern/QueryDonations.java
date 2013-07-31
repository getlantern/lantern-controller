package org.lantern;

import java.io.InputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

import com.googlecode.objectify.Objectify;
import com.googlecode.objectify.ObjectifyService;

import com.google.apphosting.api.DeadlineExceededException;
import com.google.appengine.api.taskqueue.Queue;
import com.google.appengine.api.taskqueue.QueueFactory;
import static com.google.appengine.api.taskqueue.TaskOptions.Builder.*;

import org.lantern.data.LatestProcessedDonation;


@SuppressWarnings("serial")
public class QueryDonations extends HttpServlet {

    private final String queryUrl =
        "https://rally.org/api/causes/iD1Ibm17EAA/donations?access_token="
        + LanternControllerConstants.getRallyAccessToken();

    private static final transient Logger log = Logger
            .getLogger(QueryDonations.class.getName());

   // https://developers.google.com/appengine/docs/java/taskqueue/#Java_Tasks_within_transactions
    private static final int MAX_TASKS_PER_TRANSACTION = 5;

    private static final int MAX_DONATIONS_PER_PAGE = 25;

    static {
        ObjectifyService.register(LatestProcessedDonation.class);
    }

    @Override
    public void doGet(final HttpServletRequest request,
                      final HttpServletResponse response) {
        try {
            while (!doOneBatch());
        } catch (final ConcurrentModificationException e) {
            log.info("Concurrent modification; exiting.");
        // Let's document this as one of the normal termination conditions and
        // prevent this from looking too alarming in the logs.
        } catch (final DeadlineExceededException e) {
            log.info("GAE timed me out; exiting.");
        }
        LanternControllerUtils.populateOKResponse(response, "OK");
    }

    /**
     * Read up to MAX_TASKS_PER_TRANSACTION donations, enqueue them for further
     * processing, and update LatestProcessedDonation.
     */
    private boolean doOneBatch() throws ConcurrentModificationException {
        final String idKey = LanternControllerConstants.DONATION_ID_KEY;
        final String emailKey = LanternControllerConstants.DONATION_EMAIL_KEY;
        final String amountKey
            = LanternControllerConstants.DONATION_AMOUNT_KEY;
        final Objectify ofy = ObjectifyService.beginTransaction();
        final Queue tq = QueueFactory.getDefaultQueue();
        try {
            LatestProcessedDonation latest = ofy.find(
                    LatestProcessedDonation.class,
                    LatestProcessedDonation.singletonId);
            if (latest == null) {
                latest = new LatestProcessedDonation();
            }
            List<Map<String, Object>> dons
                = parseJson(getInputStream(latest));
            int begin = latest.getIndex() + 1;
            // Not inclusive.
            int end = Math.min(begin + MAX_TASKS_PER_TRANSACTION,
                               dons.size());
            for (int i=begin; i<end; i++) {
                Map<String, Object> don = dons.get(i);
                final String id = don.get(idKey).toString();
                final String email = don.get(emailKey).toString();
                final String amount = don.get(amountKey).toString();
                log.info("Trying to enqueue " + amount + "-cent donation "
                         + id + " from " + email);
                // These endpoints will be called iff the transaction succeeds.
                // Also, App Engine will make sure to keep retrying as long as
                // we don't return 200 OK.
                //
                // See https://developers.google.com/appengine/docs/java/taskqueue/#Java_Tasks_within_transactions
                tq.add(withUrl("/process_donation")
                       .param(idKey, id)
                       .param(emailKey, email)
                       .param(amountKey, amount));
            }
            //XXX: consider date changes!
            if (end < MAX_DONATIONS_PER_PAGE) {
                latest.setIndex(end - 1);
            } else {
                latest.incrementPage();
                latest.setIndex(-1);
            }
            ofy.put(latest);
            ofy.getTxn().commit();
            return (end == dons.size()
                    && dons.size() < MAX_DONATIONS_PER_PAGE);
        } finally {
            if (ofy.getTxn().isActive()) {
                ofy.getTxn().rollback();
            }
        }
    }

    private InputStream getInputStream(LatestProcessedDonation d) {
        String url = String.format("%s&start_date=%d/%02d/%02d&page=%d",
                                   queryUrl, d.getYear(), d.getMonth(),
                                   d.getDay(), d.getPage());
        log.info("Fetching URL " + url);
        try {
            return new URL(url).openStream();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<Map<String, Object>> parseJson(InputStream is) {
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
