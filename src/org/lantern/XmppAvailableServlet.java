package org.lantern;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.mrbean.MrBeanModule;
import org.lantern.data.Dao;
import org.littleshoot.util.Sha1Hasher;

import com.google.appengine.api.xmpp.Message;
import com.google.appengine.api.xmpp.MessageBuilder;
import com.google.appengine.api.xmpp.MessageType;
import com.google.appengine.api.xmpp.Presence;
import com.google.appengine.api.xmpp.SendResponse;
import com.google.appengine.api.xmpp.XMPPService;
import com.google.appengine.api.xmpp.XMPPServiceFactory;
import com.google.appengine.labs.repackaged.org.json.JSONObject;

@SuppressWarnings("serial")
public class XmppAvailableServlet extends HttpServlet {
    
    private final transient Logger log = Logger.getLogger(getClass().getName());
    
    @Override
    public void doPost(final HttpServletRequest req, 
        final HttpServletResponse res) {
        log.info("Got XMPP post...");
        final XMPPService xmpp = XMPPServiceFactory.getXMPPService();
        final Presence presence;
        try {
            presence = xmpp.parsePresence(req);
        } catch (final IOException e) {
            log.severe("Could not parse presence: "+e.getMessage());
            return;
        }
        
        if (isInvite(presence)) {
            
            final String from = LanternControllerUtils.userId(presence);
            final Dao dao = new Dao();
            if (!dao.hasMoreInvites(from)) {
                log.severe("No more invites for user: "+from);
                return;
            }
            try {
                processInvite(presence);
            } catch (final AlreadyInvitedException e) {
                return;
            }
            dao.decrementInvites(from);
            return;
        }
        final boolean available = presence.isAvailable();
        
        
        //final boolean lan = LanternControllerUtils.isLantern(id);
        log.info("Got presence "+available);
        
        log.info("Status: '"+presence.getStatus()+"'");
        final String stanza = presence.getStanza();
        log.info("Stanza: "+stanza);
        final String stats = StringUtils.substringBetween(stanza, 
            "<property><name>stats</name><value type=\"string\">", "</value></property>");
        //log.info("Stats JSON: "+stats);
        
        final boolean isGiveMode = 
            LanternControllerUtils.isLantern(presence.getFromJid().getId());
        
        
        final Map<String,Object> responseJson = 
            new LinkedHashMap<String,Object>();
        final String userId = userId(presence, isGiveMode);
        processClientInfo(presence, stats, responseJson, userId);
        if (isGiveMode) {
            processGiveMode(presence, xmpp, available, responseJson);
        } else {
            processGetMode(presence, xmpp, available, responseJson);
        }
    }

    private static final String INVITE = 
        "<property><name>" + LanternConstants.INVITE_KEY + 
        "</name><value type=\"string\">";

    // query string param to bypass password wall on getlantern.org:
    private static final String ACCESSKEY = "secret"; // XXX violates DRY (duplicated in getlantern.org code's secrets.py)
    // XXX dynamically generate random s3 bucket for this:
    private static final String INSTALLER_BASE_URL = "http://s3.amazonaws.com/lantern/latest.";
    private static final String INSTALLER_URL_DMG = INSTALLER_BASE_URL + "dmg";
    private static final String INSTALLER_URL_EXE = INSTALLER_BASE_URL + "exe";
    private static final String INSTALLER_URL_DEB = INSTALLER_BASE_URL + "deb";

    private final class AlreadyInvitedException extends Exception {}

    private void processInvite(final Presence presence) 
        throws AlreadyInvitedException {
        final Dao dao = new Dao();
        final String stanza = presence.getStanza();
        final String email = StringUtils.substringBetween(stanza, INVITE, 
            "</value></property>");
        
        if (dao.isInvited(email)) {
            log.info("User is already invited: "+email);
            throw new AlreadyInvitedException();
        }
        

        // see http://mandrillapp.com/api/docs/messages.html#method=send-template
        // XXX Java equivalent of this here:
        /* $ python
         * >>> import requests, json
         * >>> requests.post(LanternControllerConstants.MANDRILL_API_SEND_TEMPLATE_URL,
         * ...   data=json.dumps(dict(
         * ...     key=LanternControllerConstants.MANDRILL_API_KEY,
         * ...     template_name=LanternControllerConstants.INVITE_EMAIL_TEMPLATE_NAME,
         * ...     template_content=[],
         * ...     message=dict(
         * ...       subject=LanternControllerConstants.INVITE_EMAIL_SUBJECT,
         * ...       from_email=LanternControllerConstants.INVITE_EMAIL_FROM_ADDRESS,
         * ...       from_name=LanternControllerConstants.INVITE_EMAIL_FROM_NAME,
         * ...       to=[dict(
         * ...         email=email, # as calculated above
         * ...         name=name, # XXX can we get this too if available?
         * ...         )],
         * ...       track_opens=True,
         * ...       track_clicks=True,
         * ...       auto_text=True,
         * ...       url_strip_qs=True,
         * ...       preserve_recipients=False,
         * ...       bcc_address='bcc@getlantern.org', # XXX use inviter_email instead?
         * ...       merge_vars=[
         * ...         {'name': 'INVITER_EMAIL', 'content': inviter_email},
         * ...         {'name': 'INVITER_NAME', 'content': inviter_name}, # XXX can we get this too if available?
         * ...         {'name': 'ACCESSKEY', 'content': ACCESSKEY},
         * ...         {'name': 'INSTALLER_URL_DMG', 'content': INSTALLER_URL_DMG},
         * ...         {'name': 'INSTALLER_URL_EXE', 'content': INSTALLER_URL_EXE},
         * ...         {'name': 'INSTALLER_URL_DEB', 'content': INSTALLER_URL_DEB},
         * ...         ],
         * ...      ),
         * ...    )
         * ...  )))
         * ...
         * >>> # now check r.json; if r.json.get('status') == 'error', we got an error
         * >>> # otherwise the call succeeded, and we'll get:
         * >>> r.json
         * [{u'email': u'recipient@example.com', u'status': u'sent'}]
         */

        final JSONObject json = new JSONObject();
        json.put("key", LanternControllerConstants.MANDRILL_API_KEY);
        json.put("template_name", LanternControllerConstants.INVITE_EMAIL_TEMPLATE_NAME);
        json.put("template_content", new String[]);
        final JSONObject msg = new JSONObject();
        msg.put("subject", LanternControllerConstants.INVITE_EMAIL_SUBJECT);
        msg.put("from_email", LanternControllerConstants.INVITE_EMAIL_FROM_ADDRESS);
        msg.put("from_name", LanternControllerConstants.INVITE_EMAIL_FROM_NAME);
        final JSONObject[] to = {
            new JSONObject() {{
                put("email", email)
             // put("name", name) // XXX can we get this too if available?
            }}
        };
        msg.put("to", to);
        msg.put("track_opens", true);
        msg.put("track_clicks", true);
        msg.put("auto_text", true);
        msg.put("url_strip_qs", true);
        msg.put("preserve_recipients", false);
        msg.put("bcc_address", "bcc@getlantern.org"); // XXX use inviter_email instead?
        final JSONObject[] mergeVars = {
            new JSONObject() {{
                put("name", "INVITER_EMAIL"),
                put("content", inviter_email) }},
            /* XXX can we get this too if available? (if so, modify template)
            new JSONObject() {{
                put("name", "INVITER_NAME"),
                put("content", inviter_name) }},
            */
            new JSONObject() {{
                put("name", "ACCESSKEY"),
                put("content", ACCESSKEY) }},
            new JSONObject() {{
                put("name", "INSTALLER_URL_DMG"),
                put("content", INSTALLER_URL_DMG) }},
            new JSONObject() {{
                put("name", "INSTALLER_URL_EXE"),
                put("content", INSTALLER_URL_EXE) }},
            new JSONObject() {{
                put("name", "INSTALLER_URL_DEB"),
                put("content", INSTALLER_URL_DEB) }}
        };
        msg.put("merge_vars", mergeVars);
        json.put("message", msg);
        final String payload = json.toJSONString();

        final Runnable r = new Runnable() {
            @Override
            public void run() {
                final HttpPost post = new HttpPost(
                    LanternControllerConstants.MANDRILL_API_SEND_TEMPLATE_URL);
                // XXX gzip
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                baos.write(payload.getBytes("UTF-8"));
                post.setEntity(new ByteArrayEntity(baos.toByteArray()));
                final DefaultHttpClient httpclient = new DefaultHttpClient();
                try {
                    final HttpResponse response = httpclient.execute(post);
                    // XXX get response json and see what happened
                } catch (Exception e) {
                    log.warning("Exception: "+e);
                    e.printStackTrace();
                }
            }
        };
        final Thread t = new Thread(r, "Mandrill-API-Thread");
        t.setDaemon(true);
        t.start();
    }

    private boolean isInvite(final Presence presence) {
        final String stanza = presence.getStanza();
        return stanza.contains(INVITE);
    }

    private String userId(final Presence presence, final boolean isGiveMode) {
        if (isGiveMode) {
            return LanternControllerUtils.userId(presence);
        } else {
            // We hash the ID of users in censored countries and just count them
            // as a generic number. We only look at the JID at all to avoid 
            // overcounting.
            return Sha1Hasher.hash(LanternControllerUtils.userId(presence));
        }
    }

    private void processGetMode(final Presence presence,
        final XMPPService xmpp, final boolean available, 
        final Map<String, Object> responseJson) {
        if (available) {
            // Not we don't tell get mode users to check back in -- we just 
            // give them servers to connect to.
            log.info("Sending servers to available get mode");
            addServers(presence.getFromJid().getId(), responseJson);
            sendResponse(presence, xmpp, responseJson);
        } else {
            log.info("Not sending servers to unavailable clients");
        }
    }

    private void processGiveMode(final Presence presence,
        final XMPPService xmpp, final boolean available, 
        final Map<String, Object> responseJson) {
        if (available) {
            // We always need to tell the client to check back in because 
            // we use it as a fallback for which users are online.
            responseJson.put(LanternConstants.UPDATE_TIME, 
                LanternControllerConstants.UPDATE_TIME_MILLIS);
            log.info("Not sending servers to give mode");
            sendResponse(presence, xmpp, responseJson);
        } else {
            log.info("Not sending servers to unavailable clients");
        }
        
        // The following will delete the instance if it's not available,
        // updating all counters.
        log.info("Setting instance availability");
        final String instanceId = presence.getFromJid().getId();
        final Dao dao = new Dao();
        dao.setInstanceAvailable(instanceId, available);
    }

    private void processClientInfo(final Presence presence, 
        final String stats, final Map<String, Object> responseJson, final String idToUse) {
        if (StringUtils.isBlank(stats)) {
            log.info("No stats to process!");
            return;
        }
        log.info("Processing stats!");
        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new MrBeanModule());
        try {
            final Stats data = mapper.readValue(stats, Stats.class);
            addUpdateData(data, responseJson);
            addInviteData(presence, responseJson);
            try {
                updateStats(data, idToUse);
            } catch (final UnsupportedOperationException e) {
                log.severe("Error updating stats: "+e.getMessage());
            }
        } catch (final JsonParseException e) {
            log.severe("Error parsing stats: "+e.getMessage());
        } catch (final JsonMappingException e) {
            log.severe("Error parsing stats: "+e.getMessage());
        } catch (final IOException e) {
            log.severe("Error parsing stats: "+e.getMessage());
        }
    }

    private void addInviteData(final Presence presence,
        final Map<String, Object> responseJson) {
        
        final Dao dao = new Dao();
        final int invites = 
            dao.getInvites(LanternControllerUtils.userId(presence));
        responseJson.put(LanternConstants.INVITES_KEY, invites);
        
    }

    private void addUpdateData(final Stats data,
        final Map<String, Object> responseJson) {
        try {
            final double version = Double.parseDouble(data.getVersion());
            //final double version = 0.001; //just for testing!!
            if (LanternControllerConstants.LATEST_VERSION > version) {
                final Map<String,Object> updateJson = 
                    new LinkedHashMap<String,Object>();
                updateJson.put(LanternConstants.UPDATE_VERSION_KEY, 
                    LanternControllerConstants.LATEST_VERSION);
                updateJson.put(LanternConstants.UPDATE_RELEASED_KEY, 
                    LanternControllerConstants.UPDATE_RELEASE_DATE);
                updateJson.put(LanternConstants.UPDATE_URL_KEY, 
                    LanternControllerConstants.UPDATE_URL);
                updateJson.put(LanternConstants.UPDATE_MESSAGE_KEY, 
                    LanternControllerConstants.UPDATE_MESSAGE);
                responseJson.put(LanternConstants.UPDATE_KEY, updateJson);
            }
        } catch (final NumberFormatException nfe) {
            // Probably running from main line.
            log.info("Format exception on version: "+data.getVersion());
        }
    }

    private void sendResponse(final Presence presence, final XMPPService xmpp, 
        final Map<String, Object> responseJson) {
        final String serversBody = LanternUtils.jsonify(responseJson);
        final Message msg = 
            new MessageBuilder().withRecipientJids(
                presence.getFromJid()).withBody(serversBody).withMessageType(
                    MessageType.HEADLINE).build();
        log.info("Sending response:\n"+responseJson.toString());
        final SendResponse status = xmpp.sendMessage(msg);
        final boolean messageSent = 
            (status.getStatusMap().get(
                presence.getFromJid()) == SendResponse.Status.SUCCESS);
    }

    private void updateStats(final Stats data, final String idToUse) {
        
        final Dao dao = new Dao();
        
        log.info("Updating stats");
        dao.updateUser(idToUse, data.getDirectRequests(), 
            data.getDirectBytes(), data.getTotalProxiedRequests(), 
            data.getTotalBytesProxied(), 
            data.getCountryCode());
    }

    private void addServers(final String jid, 
        final Map<String, Object> responseJson) {

        log.info("Adding servers...");
        final Dao dao = new Dao();
        final Collection<String> servers = dao.getInstances();
        
        // Make sure to remove ourselves.
        servers.remove(jid);
        
        // TODO: We need to provide the same servers for the same users every
        // time. Possibly only provide servers to validated users?
        
        servers.addAll(Arrays.asList("75.101.134.244:7777",
            "laeproxyhr1.appspot.com",
            "rlanternz.appspot.com"));
            
        responseJson.put(LanternConstants.SERVERS, servers);
    }
}
