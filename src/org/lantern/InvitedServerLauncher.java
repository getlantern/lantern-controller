package org.lantern;

import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.google.appengine.api.xmpp.JID;
import com.google.appengine.api.xmpp.Message;
import com.google.appengine.api.xmpp.XMPPService;
import com.google.appengine.api.xmpp.XMPPServiceFactory;
import com.google.appengine.api.xmpp.MessageBuilder;
import com.google.appengine.api.xmpp.MessageType;

import org.littleshoot.util.ThreadUtils;

import org.lantern.data.AlreadyInvitedException;
import org.lantern.data.UnknownUserException;
import org.lantern.data.Dao;


public class InvitedServerLauncher {

    private static final transient Logger log = 
        Logger.getLogger(InvitedServerLauncher.class.getName());

    public static final String PENDING = "pending";
    public static final String INVSRVLAUNCHER_EMAIL = "invsrvlauncher@gmail.com";
    private static final JID INVSRVLAUNCHER_JID = new JID(INVSRVLAUNCHER_EMAIL);

    public static void onInvite(final String inviterName,
                           final String inviterEmail, 
                           final String refreshToken, 
                           final String invitedEmail) {

        final Dao dao = new Dao();
        try {
            dao.addInvite(inviterEmail, invitedEmail);
        } catch (final AlreadyInvitedException e) {
            log.info(inviterEmail 
                     + " had already invited " 
                     + invitedEmail);
            return;
        } 
        
        String installerLocation = dao.getAndSetInstallerLocation(inviterEmail);
        if (installerLocation == null && refreshToken != null) {
            // Ask invsrvlauncher to create an instance for this user.
            log.info("Ordering launch of new invited server for "
                     + inviterEmail);
            final String bucket = dao.getAndIncrementLeastUsedBucket();
            if (bucket == null) {
                log.severe("I have no buckets to store installers for invitees!");
                return;
            }
            final XMPPService xmpp = XMPPServiceFactory.getXMPPService();
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            /* These aren't in LanternConstants because they are not handled
             * by the client, but by a Python XMPP bot.
             * (salt/invsrvlauncher/xmpp-bot.py at lantern_aws, branch
             * invsrvlauncher)
             */
            map.put("launch-invsrv-as", inviterEmail);
            map.put("launch-refrtok", refreshToken);
            map.put("launch-bucket", bucket);
            final String body = LanternUtils.jsonify(map);
            Message msg = new MessageBuilder()
                .withMessageType(MessageType.HEADLINE)
                .withRecipientJids(INVSRVLAUNCHER_JID)
                .withBody(body)
                .build();
            xmpp.sendMessage(msg);

        } else if (!installerLocation.equals(PENDING)) {
            sendInvite(inviterName, inviterEmail, invitedEmail, installerLocation);
        }
    }

    public static void onInvitedServerUp(final String inviterEmail, 
                                         final String installerLocation) {
        final Dao dao = new Dao();
        try {
            final Collection<String> invitees = dao.setInstallerLocationAndGetInvitees(inviterEmail, installerLocation);
            for (String invitedEmail : invitees) {
                sendInvite(inviterEmail, inviterEmail, invitedEmail, installerLocation);
            }
        } catch (final UnknownUserException e) {
            log.severe("Server up for unknown inviter " + inviterEmail);
        }
    }

    private static void sendInvite(final String inviterName,
                                   final String inviterEmail,
                                   final String invitedEmail,
                                   final String installerLocation) {
        final String[] parts = installerLocation.split("/");
        assert parts.length == 2;
        final String bucket = parts[0];
        final String folder = parts[1];
        final String baseUrl =
            "https://" + bucket + ".s3.amazonaws.com/" + folder
            + "/lantern-" + LanternControllerConstants.LATEST_VERSION_STRING;

        try {
            MandrillEmailer.sendInvite(inviterName, inviterEmail, invitedEmail,
                baseUrl + ".dmg", baseUrl + ".exe",
                baseUrl + "-32.deb", baseUrl + "-64.deb");
        } catch (final IOException e) {
            log.warning("Could not send e-mail!\n"+ThreadUtils.dumpStack());
        }
    }
}
