package org.lantern;

import java.net.URL;
import java.util.logging.Logger;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.apphosting.api.DeadlineExceededException;
import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.map.ObjectMapper;

import org.lantern.data.Dao;
import org.lantern.data.LanternUser;
import org.lantern.loggly.LoggerFactory;


@SuppressWarnings("serial")
public class MaintenanceTask extends HttpServlet {

    private static final transient Logger log = LoggerFactory
            .getLogger(MaintenanceTask.class);

    @Override
    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response) {
        String input = request.getParameter("input");
        log.info("Running maintenance task with input " + input);
        // This space reserved for your hacks.
        // Deploy them, run them, delete/disable them, redeploy with them deleted/disabled.
        // DON'T LEAVE THEM ENABLED, EITHER IN GITHUB OR GAE!
        //checkConfigFolders(input);
        LanternControllerUtils.populateOKResponse(response, "OK");
    }

    private void checkConfigFolders(String input) {

        String email = null;
        if (!StringUtils.isBlank(input)
            && input.matches("\\s*.+@.+\\..+\\s*")) {
            email = EmailAddressUtils.normalizedEmail(input.trim());
            log.info("Starting with email: " + email);
        } else {
            log.info("Starting from the beginning");
        }

        Dao dao = new Dao();
        int nulls = 0;
        int ok = 0;
        int fishy = 0;

        int expectedNumberOfFallbacks = 2;

        HashSet<String> tokens = new HashSet<String>();
        HashSet<String> certs = new HashSet<String>();

        try {
            for (LanternUser user : dao.ofy().query(LanternUser.class)) {
                if (email != null && !email.equals(user.getId())) {
                    continue;
                }
                email = null;
                log.info("Checking " + user.getId());
                String configFolder = user.getConfigFolder();
                if (configFolder == null) {
                    log.info("Null config!");
                    nulls += 1;
                    continue;
                }
                boolean goodToken;
                boolean goodCert;
                try {
                    URL url = new URL("https://s3-ap-southeast-1.amazonaws.com/lantern-config/"
                                      + configFolder
                                      + "/config.json");
                    Map<String, Object> m = new ObjectMapper().readValue(
                                                    url.openStream(), Map.class);
                    List<Map<String, Object>> fbs = (List)m.get("fallbacks");
                    if (fbs.size() != 1) {
                        throw new RuntimeException("Weird-sized fallback list: " + fbs.size());
                    }
                    String authToken = (String)fbs.get(0).get("auth_token");
                    String cert = (String)fbs.get(0).get("cert");

                    if (authToken == null) {
                        log.info("Null token!");
                        goodToken = false;
                    } else if (tokens.contains(authToken)) {
                        log.info("Good token.");
                        goodToken = true;
                    } else if (tokens.size() < expectedNumberOfFallbacks) {
                        log.info("Saw token " + authToken + " for the first time.  Adding.");
                        tokens.add(authToken);
                        goodToken = true;
                    } else {
                        log.info("BAD TOKEN: " + authToken);
                        goodToken = false;
                    }
                    if (cert == null) {
                        log.info("Null cert!");
                        goodCert = false;
                    } else if (certs.contains(cert)) {
                        log.info("Good cert.");
                        goodCert = true;
                    } else if (certs.size() < expectedNumberOfFallbacks) {
                        log.info("Saw cert " + cert + " for the first time.  Adding.");
                        certs.add(cert);
                        goodCert = true;
                    } else {
                        log.info("BAD cert: " + cert);
                        goodCert = false;
                    }
                    if (goodToken && goodCert) {
                        ok += 1;
                    } else {
                        fishy += 1;
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            log.info("Done!");
        } catch (DeadlineExceededException e) {
            log.info("Deadline exceeded; summary so far:");
        }
        log.info("OK: " + ok + "; fishy: " + fishy + "; null: " + nulls
                 + "; certs: " + certs.size() + "; tokens:" + tokens.size());
    }
}
