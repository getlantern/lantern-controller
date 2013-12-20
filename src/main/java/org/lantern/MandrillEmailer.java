package org.lantern;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.littleshoot.util.ThreadUtils;

import com.google.appengine.api.urlfetch.FetchOptions;
import com.google.appengine.api.urlfetch.HTTPMethod;
import com.google.appengine.api.urlfetch.HTTPRequest;
import com.google.appengine.api.urlfetch.HTTPResponse;
import com.google.appengine.api.urlfetch.URLFetchService;
import com.google.appengine.api.urlfetch.URLFetchServiceFactory;

/**
 * Class for sending e-mails using the Mandrill API.
 */
public class MandrillEmailer {

    private static final transient Logger log =
        Logger.getLogger(MandrillEmailer.class.getName());

    /**
     * Send an invite e-mail.
     *
     * @param inviterName The name of the person doing the inviting.
     * @param inviterEmail The email of the person doing the inviting.
     * @param inviteeEmail The email of the person to invite.
     * @param installerLocation The location where the installers can be found.
     * @param isAlreadyUser We send a different email if the user has ever logged in
     *
     * @see populateInstallerUrls for the format of installerLocation.
     *
     * @throws IOException If there's any error accessing Mandrill, generating
     * the JSON, etc.
     */
    public static void sendInvite(String inviterName,
                                  String inviterEmail,
                                  String inviteeEmail,
                                  String installerLocation,
                                  boolean isAlreadyUser)
            throws IOException {
        log.info("Sending invite to "+inviteeEmail);
        sendEmail(inviteJson(inviterName,
                             inviterEmail,
                             inviteeEmail,
                             installerLocation,
                             isAlreadyUser));
    }

    // Factored out and made public for testing.
    public static String inviteJson(String inviterName,
                                    String inviterEmail,
                                    String inviteeEmail,
                                    String installerLocation,
                                    boolean isAlreadyUser)
            throws IOException {
        if (StringUtils.isBlank(inviteeEmail)) {
            log.warning("No inviter e-mail!");
            throw new IOException("Invited e-mail required!!");
        }
        // XXX: Temporarily disabled because there is no such
        // "friend-notification" template at the time of this writing, and this
        // was causing send_invite tasks to crash.
        //String template
        //    = isAlreadyUser ? "friend-notification" : "invite-notification";
        String template = "invite-notification";
        String inviterNameOrEmail
            = StringUtils.isBlank(inviterName) ? inviterEmail : inviterName;
        String fromName
            = inviterNameOrEmail
              + LanternControllerConstants.INVITE_EMAIL_FROM_SUFFIX;
        Map<String, String> m = new HashMap<String,String>();
        m.put("INVITER_EMAIL", inviterEmail);
        m.put("INVITER_NAME", inviterNameOrEmail);
        populateInstallerUrls(m, installerLocation);
        return jsonToSendEmail(
                      template,
                      "Lantern Invitation",
                      fromName,
                      LanternControllerConstants.INVITE_EMAIL_FROM_ADDRESS,
                      inviteeEmail,
                      inviterEmail,
                      LanternControllerConstants.INVITE_EMAIL_BCC_ADDRESS,
                      m);
    }

    /**
     * Send a notification update e-mail.
     *
     * @param toEmail The email to which the e-mail should be sent.
     * @param version The version string to display in the e-mail.
     * @param installerLocation The location where the installers can be found.
     *
     * @see populateInstallerUrls for the format of installerLocation.
     *
     * @throws IOException If there's any error accessing Mandrill, generating
     * the JSON, etc.
     */
    public static void sendVersionUpdate(String toEmail,
                                         String version,
                                         String installerLocation)
            throws IOException {
        log.info("Sending version update notification to " + toEmail);
        Map<String, String> m = new HashMap<String,String>();
        m.put("VERSION", version);
        populateInstallerUrls(m, installerLocation);
        sendEmail(jsonToSendEmail("update-notification",
                                  "Lantern Update Available",
                                  null,
                                  null,
                                  toEmail,
                                  null,
                                  "updates@getlantern.org",
                                  m));
    }

    private static void populateInstallerUrls(Map<String, String> m,
                                              String installerLocation) {
        final String[] parts = installerLocation.split(",");
        assert parts.length == 2;
        final String folder = parts[0];
        final String version = parts[1];
        final String baseUrl =
            "https://s3.amazonaws.com/" + folder + "/lantern-net-installer_";
        m.put("INSTALLER_URL_DEB", baseUrl + "unix_" + version + ".sh");
        m.put("INSTALLER_URL_DMG", baseUrl + "macos_" + version + ".dmg");
        m.put("INSTALLER_URL_EXE", baseUrl + "windows_" + version + ".exe");
    }

    private static String jsonToSendEmail(String template,
                                          String subject,
                                          String fromName,
                                          String fromEmail,
                                          String toEmail,
                                          String replyTo,
                                          String bcc,
                                          Map<String, String> vars)
            throws IOException {
        final Map<String, Object> data = new HashMap<String, Object>();
        final Map<String, Object> msg = new HashMap<String, Object>();
        msg.put("subject", subject);
        msg.put("from_email", fromEmail == null
                              ? LanternControllerConstants.TEAM_EMAIL
                              : fromEmail);
        msg.put("from_name", fromName == null ? "Lantern Team" : fromName);
        String mandrillApiKey = LanternControllerConstants.getMandrillApiKey();
        if (mandrillApiKey == null || mandrillApiKey.equals("secret")) {
            throw new RuntimeException("Please correct your secrets file to include the Mandrill API key");
        }
        data.put("key", mandrillApiKey);

        if (replyTo != null) {
            Map<String, String> headers = new HashMap<String, String>();
            headers.put("Reply-To", replyTo);
            msg.put("headers", headers);
        }

        final Map<String, String> to = new HashMap<String, String>();
        to.put("email", toEmail);
        msg.put("to", Arrays.asList(to));
        msg.put("track_opens", true);
        msg.put("track_clicks", false);
        msg.put("auto_text", true);
        msg.put("url_strip_qs", true);
        msg.put("preserve_recipients", false);
        if (bcc != null) {
            msg.put("bcc_address", bcc);
        }

        //TODO: get language from client
        final String body = readFile("email/" + template + ".html");
        if (body == null) {
            throw new RuntimeException("Could not find template " + template);
        }

        msg.put("html", body);
        msg.put("global_merge_vars", mergeVarsFromMap(vars));
        data.put("message", msg);
        try {
            return new ObjectMapper().writeValueAsString(data);
        } catch (final JsonGenerationException e) {
            throw new IOException("Could not generate JSON", e);
        } catch (final JsonMappingException e) {
            throw new IOException("Could not map JSON", e);
        } catch (final IOException e) {
            throw e;
        }
    }

    private static String readFile(final String filename) {
        InputStream stream = MandrillEmailer.class
                .getResourceAsStream(filename);
        if (stream == null) {
            throw new RuntimeException("No such template " + filename);
        }
        String result = null;
        try {
            result = IOUtils.toString(stream, Charset.forName("UTF-8"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    public static void sendEmail(final String payload) {
        final URL url;
        try {
            url = new URL(LanternControllerConstants.MANDRILL_API_SEND_URL);
        } catch (final MalformedURLException e) {
            log.warning("Malformed: " + ThreadUtils.dumpStack());
            return;
        }

        final FetchOptions fetchOptions = FetchOptions.Builder.withDefaults().
            followRedirects().validateCertificate().setDeadline(60d);
        log.info("Sending payload:\n"+payload);
        final HTTPRequest request =
            new HTTPRequest(url, HTTPMethod.POST, fetchOptions);
            //new HTTPRequest(url, HTTPMethod.POST, fetchOptions);
        try {
            request.setPayload(payload.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            log.warning("Encoding? "+ThreadUtils.dumpStack());
            return;
        }

        final URLFetchService fetcher =
                URLFetchServiceFactory.getURLFetchService();

        try {
            final HTTPResponse response = fetcher.fetch(request);
            final int responseCode = response.getResponseCode();
            if (responseCode != 200) {
                log.warning("Response: " + responseCode);
                log.warning("Content: "+new String(response.getContent()));
            } else {
                log.info("Successfully sent to mandrill!");
            }

        } catch (IOException e) {
            log.warning("Error fetching mandrill:\n"+ThreadUtils.dumpStack());
        }
    }

    private static Map<String, String> mergeVar(final String key, final String val) {
        final Map<String, String> map = new HashMap<String, String>();
        map.put("name", key);
        map.put("content", val);
        return map;
    }

    private static List<Map<String, String>> mergeVarsFromMap(
            Map<String, String> map) {
        List<Map<String, String>> mv = new ArrayList<Map<String, String>>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            mv.add(mergeVar(entry.getKey(), entry.getValue()));
        }
        return mv;
    }

    public static void addEmailToUsersList(final String email,
            String language) {

        if (StringUtils.isEmpty(language)) {
            language = "en";
        }
        String payload = mailchimpListSubscribeJson(email,
                LanternControllerConstants.MAILCHIMP_LIST_ID, language);

        URL url = getMailchimpUrl("listSubscribe");

        final FetchOptions fetchOptions = FetchOptions.Builder.withDefaults().
            followRedirects().validateCertificate().setDeadline(60d);
        log.info("Sending payload:\n"+payload);
        final HTTPRequest request =
            new HTTPRequest(url, HTTPMethod.POST, fetchOptions);
        try {
            request.setPayload(payload.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            log.warning("Encoding? "+ThreadUtils.dumpStack());
            return;
        }

        final URLFetchService fetcher =
                URLFetchServiceFactory.getURLFetchService();

        try {
            final HTTPResponse response = fetcher.fetch(request);
            final int responseCode = response.getResponseCode();
            final String body = new String(response.getContent());
            if (responseCode != 200) {
                log.warning("Response: " + responseCode);
                log.warning("Content: " + body);
            } else {
                log.info("Sent to mailchimp, response is " + body);
            }

        } catch (IOException e) {
            log.warning("Error fetching mailchimp:\n"+ThreadUtils.dumpStack());
        }
    }

    private static URL getMailchimpUrl(String method) {
        String apiKey = LanternControllerConstants.getMailChimpApiKey();
        if (StringUtils.isBlank(apiKey)) {
            throw new RuntimeException("No MailChimp API key");
        }
        String dc = StringUtils.substringAfter(apiKey, "-");

        final String urlBase = LanternControllerConstants.MAILCHIP_API_URL_BASE;
        String url = urlBase.replace("<dc>", dc);
        url = url.replace("<method>", method);
        try {
            return new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * Creates JSON compatible with the Mailchimp API.
     * See http://apidocs.mailchimp.com/api/1.3/listsubscribe.func.php
     *
     * @param email The email address to add
     * @param listId The id of the mailing list to add them to
     * @param language See http://kb.mailchimp.com/article/can-i-see-what-languages-my-subscribers-use#code
     * @return The generated JSON to send to Mailchimp.
     * @throws IOException If there's an error generating the JSON.
     */
    public static String mailchimpListSubscribeJson(final String email,
            final String listId, final String language) {
        final ObjectMapper mapper = new ObjectMapper();
        final Map<String, Object> data = new HashMap<String, Object>();
        data.put("apikey", LanternControllerConstants.getMailChimpApiKey());
        data.put("id", listId);
        data.put("email_address", email);

        final Map<String, Object> merge_vars = new HashMap<String, Object>();
        merge_vars.put("LANG", language);

        data.put("merge_vars", merge_vars);
        data.put("double_optin", false);
        data.put("update_existing", true);
        data.put("replace_interests", false);
        data.put("send_welcome", false);

        try {
            return mapper.writeValueAsString(data);
        } catch (final JsonGenerationException e) {
            throw new RuntimeException("Could not generate JSON", e);
        } catch (final JsonMappingException e) {
            throw new RuntimeException("Could not map JSON", e);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

}
