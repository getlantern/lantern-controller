package org.lantern;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.lantern.data.Dao;
import org.lantern.loggly.LoggerFactory;


@SuppressWarnings("serial")
public class SQSChecker extends HttpServlet {
    private static final transient Logger log = LoggerFactory
            .getLogger(SQSChecker.class);

    @Override
    public void doGet(final HttpServletRequest request,
            final HttpServletResponse response) {
        List<Map<String, Object>> messages = new SQSUtil().receive();
        for (Map<String, Object> msg : messages) {
            handleMessage(msg);
        }
        LanternControllerUtils.populateOKResponse(response, "OK");
    }

    private void handleMessage(final Map<String, Object> msg) {
        /*
         * DRY Warning: The key strings in these messages are not constants
         * because they are shared with Python code (just grep for them in
         * the lantern_aws project source).
         */
       if (msg.containsKey("fp-up-id")) {
           handleFallbackProxyUp(msg);
       } else if (msg.containsKey("fp-alarm")) {
           handleFallbackProxyAlarm(msg);
       } else if (msg.containsKey("wrappers-uploaded-for")) {
           handleWrappersUploaded(msg);
       } else {
           log.severe("I don't understand this message: " + msg);
        }
    }

    private void handleFallbackProxyUp(Map<String, Object> msg) {
        String fallbackId = (String)msg.get("fp-up-id");
        if (fallbackId == null) {
            log.severe("fp-up with null fallbackId.");
            return;
        }
        String accessData = (String)msg.get("fp-up-access-data");
        if (accessData == null) {
            log.severe(fallbackId
                       + " sent fp-up with no access data.");
            return;
        }
        String ip = (String)msg.get("fp-up-ip");
        if (ip == null) {
            log.severe(fallbackId
                       + " sent fp-up with no ip.");
            return;
        }
        FallbackProxyLauncher.onFallbackProxyUp(fallbackId,
                                                accessData,
                                                ip);
    }

    private void handleFallbackProxyAlarm(Map<String, Object> sqs) {
        String key = UUID.randomUUID().toString();
        String summary = "ALARM from " + sqs.get("instance-id")
                          + "(" + sqs.get("ip") + "): "
                          + sqs.get("fp-alarm");
        new Dao().logPermanently(key, summary);
        if ((Boolean)sqs.get("send-email")) {
            try {
                Message mail = new MimeMessage(
                        Session.getDefaultInstance(new Properties(), null));
                //XXX: create a user for this.
                mail.setFrom(new InternetAddress("aranhoide@gmail.com",
                                                "Fallback Proxy Alarms"));
                //XXX: create a google group to receive these.
                mail.addRecipient(Message.RecipientType.TO,
                        new InternetAddress("aranhoide@gmail.com",
                                            "Fallback Proxy Alarms"));
                mail.setSubject(summary);
                mail.setText("instanceId: " + sqs.get("instance-id")
                             + "\nip address: " + sqs.get("ip")
                             + "\nport: " + sqs.get("port")
                             + "\ndetails: " + sqs.get("fp-alarm"));
                Transport.send(mail);
                log.info("Sent warning mail.");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            log.info("Email not requested.");
        }
    }

    private void handleWrappersUploaded(Map<String, Object> msg) {
        String userId = (String)msg.get("wrappers-uploaded-for");
        log.info("Wrappers uploaded for " + userId);
        Dao dao = new Dao();
        dao.setWrappersUploaded(userId);
        dao.sendInvitesTo(userId);
    }
}
