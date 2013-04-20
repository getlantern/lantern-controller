package org.lantern;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

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
     * Sends a Lantern invite e-mail using the Mandrill API.
     * 
     * @param inviterName The name of the person doing the inviting.
     * @param inviterEmail The email of the person doing the inviting.
     * @param invitedEmail The email of the person to invite. 
     * @param osxInstallerUrl The URL of the OS X installer.
     * @param winInstallerUrl The URL of the Windows installer.
     * @param deb32InstallerUrl The URL of the Ubuntu 32-bit installer.
     * @param deb64InstallerUrl The URL of the Ubuntu 64-bit installer.
     * @throws IOException If there's any error accessing Mandrill, generating
     * the JSON, etc.
     */
    public static void sendInvite(final String inviterName, 
        final String inviterEmail, final String invitedEmail,
        final String osxInstallerUrl, final String winInstallerUrl,
        final String deb32InstallerUrl, final String deb64InstallerUrl)
        throws IOException {
        log.info("Sending invite to "+invitedEmail);
        if (StringUtils.isBlank(invitedEmail)) {
            log.warning("No inviter e-mail!");
            throw new IOException("Invited e-mail required!!");
        }
        final String json = 
            mandrillJson(inviterName, inviterEmail, invitedEmail, 
                osxInstallerUrl, winInstallerUrl, deb32InstallerUrl,
                deb64InstallerUrl);
        sendEmail(json);
    }
    
    /**
     * Creates JSON compatible with the Mandrill API. Public for testing.
     * See https://mandrillapp.com/api/docs/messages.html#method=send-template
     * 
     * @param inviterName The name of the person doing the inviting.
     * @param inviterEmail The email of the person doing the inviting.
     * @param invitedEmail The email of the person to invite. 
     * @param osxInstallerUrl The URL of the OS X installer.
     * @param winInstallerUrl The URL of the Windows installer.
     * @param deb32InstallerUrl The URL of the Ubuntu 32-bit installer.
     * @param deb64InstallerUrl The URL of the Ubuntu 64-bit installer.
     * @return The generated JSON to send to Mandrill.
     * @throws IOException If there's an error generating the JSON.
     */
    public static String mandrillJson(final String inviterName, 
        final String inviterEmail, final String invitedEmail,
        final String osxInstallerUrl, final String winInstallerUrl,
        final String deb32InstallerUrl, final String deb64InstallerUrl) 
        throws IOException {
        final ObjectMapper mapper = new ObjectMapper();
        final Map<String, Object> data = new HashMap<String, Object>();
        data.put("key", LanternControllerConstants.getMandrillApiKey());
        data.put("template_name", LanternControllerConstants.INVITE_EMAIL_TEMPLATE_NAME);
        data.put("template_content", new String[]{});
     
        final Map<String, Object> msg = new HashMap<String, Object>();
        
        msg.put("subject", LanternControllerConstants.INVITE_EMAIL_SUBJECT);
        msg.put("from_email", LanternControllerConstants.INVITE_EMAIL_FROM_ADDRESS);
        msg.put("from_name", LanternControllerConstants.INVITE_EMAIL_FROM_NAME);
        final Map<String, String> to = new HashMap<String, String>();
        //XXX: Temporary hack to tightly control what installers testers get.
        //to.put("email", invitedEmail);
        to.put("email", "bns-ops@googlegroups.com");
        msg.put("to", Arrays.asList(to));
        msg.put("track_opens", false);
        msg.put("track_clicks", false);
        msg.put("auto_text", true);
        msg.put("url_strip_qs", true);
        msg.put("preserve_recipients", false);
        msg.put("bcc_address", LanternControllerConstants.INVITE_EMAIL_BCC_ADDRESS);
        final List<Map<String, String>> mergeVars = 
            new ArrayList<Map<String,String>>();
        if (StringUtils.isNotBlank(inviterEmail)) {
            mergeVars.add(mergeVar("INVITER_EMAIL", inviterEmail));
        }
        if (StringUtils.isNotBlank(inviterName)) {
            mergeVars.add(mergeVar("INVITER_NAME", inviterName));
        }

        mergeVars.add(mergeVar("OSXINSTALLERURL", osxInstallerUrl));
        mergeVars.add(mergeVar("WININSTALLERURL", winInstallerUrl));
        mergeVars.add(mergeVar("DEB32INSTALLERURL", deb32InstallerUrl));
        mergeVars.add(mergeVar("DEB64INSTALLERURL", deb64InstallerUrl));

        msg.put("global_merge_vars", mergeVars);
        
        data.put("message", msg);
        try {
            return mapper.writeValueAsString(data);
        } catch (final JsonGenerationException e) {
            throw new IOException("Could not generate JSON", e);
        } catch (final JsonMappingException e) {
            throw new IOException("Could not map JSON", e);
        } catch (final IOException e) {
            throw e;
        }
    }


    public static void sendEmail(final String payload) {
        final URL url;
        try {
            url = new URL(LanternControllerConstants.MANDRILL_API_SEND_TEMPLATE_URL);
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

    private static Map<String, String> mergeVar(final String key, final String val) {
        final Map<String, String> map = new HashMap<String, String>();
        map.put("name", key);
        map.put("content", val);
        return map;
    }
}
