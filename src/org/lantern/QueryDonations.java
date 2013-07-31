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
import com.google.appengine.api.taskqueue.TaskOptions;
import com.google.appengine.api.utils.SystemProperty;

import org.lantern.data.DonationCursor;


@SuppressWarnings("serial")
public class QueryDonations extends HttpServlet {

    private final String queryUrl =
        "https://rally.org/api/causes/iD1Ibm17EAA/donations?access_token="
        + LanternControllerConstants.getRallyAccessToken();

    // For testing.
    /*private String queryUrl = "http://" + SystemProperty.applicationId.get()
                              + ".appspot.com/mock_rally?";*/

    private static final transient Logger log = Logger
            .getLogger(QueryDonations.class.getName());

   // https://developers.google.com/appengine/docs/java/taskqueue/#Java_Tasks_within_transactions
    private static final int MAX_TASKS_PER_TRANSACTION = 5;

    public static final int MAX_DONATIONS_PER_PAGE = 25;

    static {
        ObjectifyService.register(DonationCursor.class);
    }

    @Override
    public void doGet(final HttpServletRequest request,
                      final HttpServletResponse response) {
        try {
            while (!doOneBatch()) {
                // Let the Datastore catch breath; we get spurious
                // `ConcurrentModificationException`s otherwise.
                Thread.sleep(1000);
            }
        // As soon as we detect any overlap we bail out to prevent waste.
        } catch (final ConcurrentModificationException e) {
            log.info("Concurrent modification; exiting.");
        // Let's document this as one of the normal termination conditions and
        // prevent this from looking too alarming in the logs.
        } catch (final DeadlineExceededException e) {
            log.info("GAE timed me out; exiting.");
        // Required by `Thread.sleep`.
        } catch (final InterruptedException e) {
            log.info("Interrupted while sleeping; exiting.");
        }
        LanternControllerUtils.populateOKResponse(response, "OK");
    }

    /**
     * Read up to MAX_TASKS_PER_TRANSACTION donations, enqueue them for further
     * processing, and update DonationCursor.
     *
     * @return true when done with all donations.
     */
    private boolean doOneBatch() throws ConcurrentModificationException {
        final String idKey = LanternControllerConstants.DONATION_ID_KEY;
        final String emailKey = LanternControllerConstants.DONATION_EMAIL_KEY;
        final String amountKey
            = LanternControllerConstants.DONATION_AMOUNT_KEY;
        final Objectify ofy = ObjectifyService.beginTransaction();
        final Queue tq = QueueFactory.getDefaultQueue();
        try {
            DonationCursor cursor = ofy.find(
                    DonationCursor.class,
                    DonationCursor.singletonId);
            if (cursor == null) {
                cursor = new DonationCursor();
            }
            List<Map<String, Object>> dons = parseJson(getInputStream(cursor));
            int begin = cursor.getIndex();
            // Not inclusive.
            int end = Math.min(begin + MAX_TASKS_PER_TRANSACTION,
                               dons.size());

            // The loop and the return statement at the end would work alright
            // without this, but we'd be checking anyway to avoid rewriting
            // an unchanged cursor, so we might just as well bail here.
            if (begin >= end) {
                return true;
            }

            String lastDate = cursor.getDate();
            // This will become the index of the first donation that has the
            // same date as the last donation of this batch.  We use this to
            // update the date in the cursor.
            int firstWithLastDate = begin;

            for (int i=begin; i<end; i++) {
                Map<String, Object> don = dons.get(i);
                final String id = don.get(idKey).toString();
                final String email = don.get(emailKey).toString();
                final String amount = don.get(amountKey).toString();
                log.info("Trying to enqueue " + amount + "-cent donation "
                         + id + " from " + email);
                // This endpoint will be called iff the transaction succeeds.
                // (I have checked that this also applies to transactions
                // started via Objectify, by commenting out the commit below
                // and noting that the log above gets triggered but the
                // endpoint doesn't get requests).
                //
                // Also, App Engine will make sure to keep retrying as long as
                // we don't return 200 OK.
                //
                // See https://developers.google.com/appengine/docs/java/taskqueue/#Java_Tasks_within_transactions
                tq.add(TaskOptions.Builder.withUrl("/process_donation")
                       .param(idKey, id)
                       .param(emailKey, email)
                       .param(amountKey, amount));
                final String don_date = don.get("created_at").toString()
                                           .split(" ")[0];
                // The rally.org API docs [1] say donations come in
                // chronological order and that the date format is YYYY-MM-DD,
                // which can be compared lexicographically.
                //
                // [1] https://rally.org/corp/dev
                if (don_date.compareTo(lastDate) > 0) {
                    lastDate = don_date;
                    firstWithLastDate = i;
                }
            }
            // We have made sure above that we've gotten at least one result.
            cursor.setDonationId(dons.get(end-1).get(idKey).toString());
            if (lastDate != cursor.getDate()) {
                cursor.setDate(lastDate);
                // Since MAX_TASKS_PER_TRANSACTION is much smaller than
                // MAX_DONATIONS_PER_PAGE, we're not past the first page of
                // results for the new start_date.
                cursor.setPage(1);
                cursor.setIndex(end - firstWithLastDate);
            } else if (end < MAX_DONATIONS_PER_PAGE) {
                cursor.setIndex(end);
            } else {
                cursor.incrementPage();
                cursor.setIndex(0);
            }
            ofy.put(cursor);
            ofy.getTxn().commit();
            // We're done when we have processed up to the end of a page
            // shorter than MAX_DONATIONS_PER_PAGE.
            return (end == dons.size()
                    && dons.size() < MAX_DONATIONS_PER_PAGE);
        } finally {
            if (ofy.getTxn().isActive()) {
                ofy.getTxn().rollback();
            }
        }
    }

    private InputStream getInputStream(DonationCursor d) {
        String url = String.format("%s&start_date=%s&page=%d",
                                   queryUrl, d.getDate(), d.getPage());
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
