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

    private static final transient Logger log
        = Logger.getLogger(MandrillEmailer.class.getName());

    /**
     * Sends a Lantern invite e-mail using the Mandrill API.
     *
     * @param inviterName The name of the person doing the inviting.
     * @param inviterEmail The email of the person doing the inviting.
     * @param invitedEmail The email of the person to invite.
     * @param osxInstallerUrl The URL of the OS X installer.
     * @param winInstallerUrl The URL of the Windows installer.
     * @param linuxInstallerUrl The URL of the Ubuntu installer.
     * @throws IOException If there's any error accessing Mandrill, generating
     * the JSON, etc.
     */
    public static void sendInvite(final String inviterName,
        final String inviterEmail, final String invitedEmail,
        final String osxInstallerUrl, final String winInstallerUrl,
        final String linuxInstallerUrl)
        throws IOException {
        log.info("Sending invite to "+invitedEmail);
        if (StringUtils.isBlank(invitedEmail)) {
            log.warning("No inviter e-mail!");
            throw new IOException("Invited e-mail required!!");
        }
        final String json =
            mandrillSendInviteEmailJson(inviterName, inviterEmail,
                invitedEmail, osxInstallerUrl, winInstallerUrl,
                linuxInstallerUrl);
        sendEmail(json);
    }

    public static void sendCreditWarning(
            final String email, final int balance) throws IOException {
        final List<Map<String, String>> mv =
            new ArrayList<Map<String,String>>();
        addMergeVar(mv, "EMAIL", email);
        addMergeVar(mv, "BALANCE", formatCents(balance));
        addMergeVar(mv, "MONTHLY", formatCents(
                    LanternControllerConstants.PROXY_MONTHLY_COST));
        addMergeVar(mv, "RALLYPAGE", LanternControllerConstants.RALLY_PAGE);
        sendEmail(mandrillSendEmailJson(
                      email,
                      "credit-warning",
                      "Your Lantern server's credit is running out",
                      mv));
    }

    public static void sendProxyCharged(
            final String email, final int centsCharged, final int balance)
            throws IOException {
        final List<Map<String, String>> mv =
            new ArrayList<Map<String,String>>();
        addMergeVar(mv, "QUANTITY", formatCents(centsCharged));
        addMergeVar(mv, "BALANCE", formatCents(balance));
        sendEmail(mandrillSendEmailJson(
                      email,
                      "proxy-charged",
                      "Server monthly payment processed",
                      mv));
    }

    public static void sendPaymentReceived(
            final String email, final int amountCents, final int balance)
            throws IOException {
        final List<Map<String, String>> mv =
            new ArrayList<Map<String,String>>();
        addMergeVar(mv, "QUANTITY", formatCents(amountCents));
        addMergeVar(mv, "BALANCE", formatCents(balance));
        sendEmail(mandrillSendEmailJson(
                      email,
                      "payment-received",
                      "Payment received",
                      mv));
    }

    public static void sendInsufficientBalance(
            final String email, final int amountCents, final int balance)
            throws IOException {
        final List<Map<String, String>> mv =
            new ArrayList<Map<String,String>>();
        final int minimum = LanternControllerConstants.LAUNCH_UPFRONT_COST;
        final int remaining = minimum - balance;
        addMergeVar(mv, "QUANTITY", formatCents(amountCents));
        addMergeVar(mv, "BALANCE", formatCents(balance));
        addMergeVar(mv, "MINIMUM", formatCents(minimum));
        addMergeVar(mv, "REMAINING", formatCents(remaining));
        addMergeVar(mv, "RALLY_PAGE", LanternControllerConstants.RALLY_PAGE);
        addMergeVar(mv, "EMAIL", email);
        sendEmail(mandrillSendEmailJson(
                      email,
                      "insufficient-balance",
                      "Payment received",
                      mv));
    }

    public static void sendProxyLaunching(final String email)
            throws IOException {
        final List<Map<String, String>> mv =
            new ArrayList<Map<String,String>>();
        sendEmail(mandrillSendEmailJson(
                      email,
                      "proxy-launching",
                      "Lantern server launching",
                      mv));
    }

    public static void sendProxyShutdown(final String email)
            throws IOException {
        final List<Map<String, String>> mv =
            new ArrayList<Map<String,String>>();
        addMergeVar(mv, "RALLY_PAGE", LanternControllerConstants.RALLY_PAGE);
        addMergeVar(mv, "EMAIL", email);
        sendEmail(mandrillSendEmailJson(
                      email,
                      "proxy-shutdown",
                      "Lantern server shut down",
                      mv));
    }

    private static String formatCents(int centsAmount) {
        int dollars = (int) centsAmount / 100;
        int cents = centsAmount % 100;
        if (cents == 0) {
            return dollars + " USD";
        } else {
            //XXX i18n
            return String.format("%d.%02d USD", dollars, cents);
        }
    }

    /**
     * Send an e-mail requesting a sponsor to log in to activate their proxy.
     *
     * @param email The email of the sponsor
     * @param osxInstallerUrl The URL of the OS X installer.
     * @param winInstallerUrl The URL of the Windows installer.
     * @param linuxInstallerUrl The URL of the Ubuntu installer.
     * @throws IOException If there's any error accessing Mandrill, generating
     * the JSON, etc.
     */
    public static void sendTokenRequest(final String email,
        final String osxInstallerUrl, final String winInstallerUrl,
        final String linuxInstallerUrl) throws IOException {
        log.info("Sending token request to " + email);
        sendInstallerEmail(
            email, osxInstallerUrl, winInstallerUrl, linuxInstallerUrl,
            "token-request",
            "Log in to complete setup");
    }

    /**
     * Send an e-mail notifying the user that the proxy they sponsor is ready.
     *
     * @param email The email of the sponsor
     * @param osxInstallerUrl The URL of the OS X installer.
     * @param winInstallerUrl The URL of the Windows installer.
     * @param linuxInstallerUrl The URL of the Ubuntu installer.
     * @throws IOException If there's any error accessing Mandrill, generating
     * the JSON, etc.
     */
    public static void sendProxyReady(final String email,
        final String osxInstallerUrl, final String winInstallerUrl,
        final String linuxInstallerUrl) throws IOException {
        log.info("Sending proxy-ready notification to " + email);
        sendInstallerEmail(email, osxInstallerUrl, winInstallerUrl,
                           linuxInstallerUrl, "proxy-ready",
                           "Your Lantern server is ready");
    }

    public static void sendInstallerEmail(final String email,
        final String osxInstallerUrl, final String winInstallerUrl,
        final String linuxInstallerUrl, final String templateName,
        final String subject)
        throws IOException {
        if (StringUtils.isBlank(email)) {
            throw new IOException("Blank e-mail address.");
        }
        final List<Map<String, String>> mv =
            new ArrayList<Map<String,String>>();
        addMergeVar(mv, "OSXINSTALLERURL", osxInstallerUrl);
        addMergeVar(mv, "WININSTALLERURL", winInstallerUrl);
        addMergeVar(mv, "LINUXINSTALLERURL", linuxInstallerUrl);
        sendEmail(mandrillSendEmailJson(
                    email, templateName, subject, mv));
    }

    private static String mandrillSendEmailJson(
            final String email,
            final String template,
            final String subject,
            final List<Map<String, String>> mergeVars) throws IOException {
        final Map<String, Object> data = new HashMap<String, Object>();
        data.put("template_name", template);
        data.put("template_content", new String[]{});
        final Map<String, Object> msg = new HashMap<String, Object>();
        msg.put("subject", subject);
        msg.put("from_email", LanternControllerConstants.ADMIN_EMAIL);
        msg.put("from_name", LanternControllerConstants.EMAIL_FROM_NAME);
        String mandrillApiKey = LanternControllerConstants.getMandrillApiKey();
        if (mandrillApiKey == null || mandrillApiKey.equals("secret")) {
            throw new RuntimeException("Please correct your secrets file to include the Mandrill API key");
        }
        data.put("key", mandrillApiKey);

        final Map<String, String> to = new HashMap<String, String>();
        to.put("email", email);
        msg.put("to", Arrays.asList(to));
        msg.put("track_opens", false);
        msg.put("track_clicks", false);
        msg.put("auto_text", true);
        msg.put("url_strip_qs", true);
        msg.put("preserve_recipients", false);
        msg.put("bcc_address", LanternControllerConstants.ADMIN_EMAIL);

        String body = getTemplate(template);
        if (body == null) {
            throw new RuntimeException("Could not find template invite-notification");
        }

        msg.put("html", body);
        msg.put("global_merge_vars", mergeVars);
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

    /**
     * Creates JSON compatible with the Mandrill API. Public for testing.
     * See https://mandrillapp.com/api/docs/messages.html#method=send
     *
     * @param inviterName The name of the person doing the inviting.
     * @param inviterEmail The email of the person doing the inviting.
     * @param invitedEmail The email of the person to invite.
     * @param osxInstallerUrl The URL of the OS X installer.
     * @param winInstallerUrl The URL of the Windows installer.
     * @param linuxInstallerUrl The URL of the Ubuntu installer.
     * @return The generated JSON to send to Mandrill.
     * @throws IOException If there's an error generating the JSON.
     */
    public static String mandrillSendInviteEmailJson(final String inviterName,
        final String inviterEmail, final String invitedEmail,
        final String osxInstallerUrl, final String winInstallerUrl,
        final String linuxInstallerUrl)
        throws IOException {
        final List<Map<String, String>> mv =
            new ArrayList<Map<String,String>>();
        if (StringUtils.isNotBlank(inviterEmail)) {
            addMergeVar(mv, "INVITER_EMAIL", inviterEmail);
        }
        if (StringUtils.isBlank(inviterName)) {
            addMergeVar(mv, "INVITER_NAME", inviterEmail);
        } else {
            addMergeVar(mv, "INVITER_NAME", inviterName);
        }
        addMergeVar(mv, "OSXINSTALLERURL", osxInstallerUrl);
        addMergeVar(mv, "WININSTALLERURL", winInstallerUrl);
        addMergeVar(mv, "LINUXINSTALLERURL", linuxInstallerUrl);
        return mandrillSendEmailJson(
                invitedEmail,
                "invite-notification",
                "Lantern Invitation",
                mv);
    }


    private static String getTemplate(String name) {
        String filename = name + ".html";
        InputStream stream = MandrillEmailer.class
                .getResourceAsStream(filename);
        if (stream == null) {
            throw new RuntimeException("No such template " + name);
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
        //final Queue queue = QueueFactory.getDefaultQueue();
        //queue.add(withPayload(task));
        //task.run();
    }

    private static void addMergeVar(List<Map<String, String>> mergeVars,
                                    final String key, final String value) {
        mergeVars.add(mergeVar(key, value));
    }

    private static Map<String, String> mergeVar(final String key, final String val) {
        final Map<String, String> map = new HashMap<String, String>();
        map.put("name", key);
        map.put("content", val);
        return map;
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
