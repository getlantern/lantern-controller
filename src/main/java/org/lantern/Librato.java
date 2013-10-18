package org.lantern;

import static com.google.appengine.api.urlfetch.FetchOptions.Builder.*;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.appengine.api.urlfetch.HTTPHeader;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;
import com.google.appengine.repackaged.org.apache.commons.codec.binary.Base64;
import com.jayway.jsonpath.JsonPath;

/**
 * Encapsulates operations related to Librato metrics.
 */
public class Librato {
    private static final transient Logger LOGGER =
            Logger.getLogger(Librato.class.getName());

    private static final String BASE_LIBRATO_COUNT_URL =
            "https://metrics-api.librato.com/v1/metrics/LanternStat_countOfDistinctProxiedClientAddresses?sources[]=%1$s&summarize_time=1&start_time=%2$s&end_time=%3$s&resolution=3600";
    private static final String LIBRATO_USERNAME = "ox@getlantern.org";
    private static final String LIBRATO_PASSWORD = "501977a876d254750019eb9bf546ac476e0e34c8579d44de3a24df68f84e18dc";
    private static final long SECONDS_IN_ONE_MONTH = 31l * 24l * 60l * 60l;

    /**
     * For the given instanceId, this returns the maximum number of distinct
     * clients (based on IP) that connected to the given instance during a run
     * of that instance. This provides one way of undertanding how heavily
     * utilized the given proxy is.
     * 
     * @param instanceId
     * @return
     */
    public static double getMaximumClientCountForProxyInLastMonth(
            String instanceId) {
        String sourceName = String.format("proxy-%1$s", instanceId);
        String json = fetchLibratoClientCountMetric(sourceName);
        if (json == null) {
            // If we couldn't fetch the json, return 0
            return 0;
        }
        return getMaxFromSummarizedMetric(json, sourceName);
    }

    /**
     * This method extracts the "max" value for the summarized metric
     * corresponding to the given sourceName.
     * 
     * @param json
     * @param sourceName
     * @return
     */
    static double getMaxFromSummarizedMetric(String json, String sourceName) {
        String pathExpression = String.format("$.measurements.%1$s[0].max",
                sourceName);
        LOGGER.info(String.format("Getting value at path '%1$s' from\n%2$s",
                pathExpression, json));
        try {
            return (Double) JsonPath.read(json, pathExpression);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, String.format(
                    "Unable to extract client count metric for %1$s.",
                    sourceName), e);
            return 0;
        }
    }

    /**
     * <p>
     * Fetches the JSON for the client count, which will look like this:
     * </p>
     * 
     * <pre>
     * {
     *     "attributes": {
     *         "aggregate": true,
     *         "created_by_ua": "metrics-librato/3.0.1 metrics/3.0.1.RC1 librato-java/0.1.1",
     *         "display_stacked": false,
     *         "display_units_long": "# of Distinct Clients",
     *         "gap_detection": true,
     *         "summarize_function": "max"
     *     },
     *     "description": null,
     *     "display_name": null,
     *     "measurements": {
     *         "proxy-429e523560d0f39949843833f05c808e": [
     *             {
     *                 "count": 518,
     *                 "max": 1.0,
     *                 "max_avg": 0.8888888888888888,
     *                 "max_sum": 16.0,
     *                 "measure_time": 1380002400,
     *                 "min": 0.0,
     *                 "sum": 16.0,
     *                 "sum_maxes": 1.0,
     *                 "sum_means": 0.8888888888888888,
     *                 "sum_squares": 16.0,
     *                 "summarized": 12,
     *                 "value": 0.03088803088803089
     *             }
     *         ]
     *     },
     *     "name": "LanternStat_countOfDistinctProxiedClientAddresses",
     *     "period": null,
     *     "resolution": 3600,
     *     "source_display_names": {},
     *     "type": "gauge"
     * }
     * </pre>
     * 
     * @param sourceName
     * @return the JSON or null if it couldn't be fetched
     */
    private static String fetchLibratoClientCountMetric(String sourceName) {
        long now = System.currentTimeMillis() / 1000;
        long oneMonthAgo = now - SECONDS_IN_ONE_MONTH;
        URLFetchService urlFetchService = URLFetchServiceFactory
                .getURLFetchService();
        try {
            URL url = new URL(String.format(
                    BASE_LIBRATO_COUNT_URL,
                    URLEncoder.encode(sourceName, "UTF-8"),
                    oneMonthAgo,
                    now));
            HTTPRequest request = new HTTPRequest(url, HTTPMethod.GET,
                    validateCertificate());
            String usernamePassword = String.format("%1$s:%2$s",
                    LIBRATO_USERNAME, LIBRATO_PASSWORD);
            request.addHeader(new HTTPHeader("Authorization", "Basic " + Base64
                    .encodeBase64String(usernamePassword.getBytes())));
            LOGGER.info(String.format("Sending request to Librato: %1$s", url));
            HTTPResponse response = urlFetchService.fetch(request);
            // We assume UTF-8 encoding
            String content = new String(response.getContent(),
                    Charset.forName("UTF-8"));
            int statusCode = response.getResponseCode();
            if (statusCode != 200) {
                LOGGER.warning(String
                        .format("Unable to get maximum client count for %1$s.  Status: %2$s   Content: %3$s",
                                sourceName, statusCode, content));
                return null;
            }
            return content;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, String.format(
                    "Unable to get maximum client count for %1$s.",
                    sourceName), e);
            return null;
        }
    }
}
