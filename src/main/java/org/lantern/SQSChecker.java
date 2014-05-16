package org.lantern;

import java.io.IOException;
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

import org.apache.commons.lang3.StringUtils;
import com.google.appengine.api.taskqueue.QueueFactory;
import com.google.appengine.api.taskqueue.TaskOptions;

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
       if (msg.containsKey("fp-up-user")) {
           handleFallbackProxyUp(msg);
       } else if (msg.containsKey("fp-alarm")) {
           handleFallbackProxyAlarm(msg);
       } else if (msg.containsKey("port-users")) {
           portUsers(msg);
       } else {
           log.severe("I don't understand this message: " + msg);
       }
    }

    private void portUsers(Map<String, Object> msg) {
        QueueFactory.getDefaultQueue().add(
            TaskOptions.Builder
               .withUrl(PortUsersTask.PATH)
               .param(PortUsersTask.ARGS, (String)msg.get("port-users")));
    }

    private void handleFallbackProxyUp(Map<String, Object> msg) {
        String fallbackProxyUserId = (String)msg.get("fp-up-user");
        if (fallbackProxyUserId == null) {
            log.severe("fp-up with null fallbackProxyUserId.");
            return;
        }
        String instanceId = (String)msg.get("fp-up-instance");
        if (instanceId == null) {
            log.severe(fallbackProxyUserId
                       + " sent fp-up with no instance ID.");
            return;
        }
        String accessData = (String)msg.get("fp-up-access-data");
        if (accessData == null) {
            log.severe(instanceId
                       + " sent fp-up with no access data.");
            return;
        }
        String ip = (String)msg.get("fp-up-ip");
        if (ip == null) {
            log.severe(instanceId
                       + " sent fp-up with no ip.");
            return;
        }
        String port = (String)msg.get("fp-up-port");
        if (port == null) {
            log.severe(instanceId
                       + " sent fp-up with no port.");
            return;
        }
        FallbackProxyLauncher.onFallbackProxyUp(fallbackProxyUserId,
                                                instanceId,
                                                accessData,
                                                ip,
                                                port);
    }

    private void handleFallbackProxyAlarm(Map<String, Object> sqs) {
        String key = UUID.randomUUID().toString();
        /* Forward compatibility: we're changing this soon. */
        String fallbackId = (String)sqs.get("fallback-id");
        if (fallbackId == null) {
            fallbackId = (String)sqs.get("instance-id");
        }
        String ip = (String)sqs.get("ip");
        String details = (String)sqs.get("fp-alarm");
        String summary = "ALARM from " + fallbackId
                          + "(" + ip + "): "
                          + details;
        new Dao().logPermanently(key, summary);
        String subject = (String)sqs.get("subject");
        if (StringUtils.isEmpty(subject)) {
            subject = "ALARM from " + fallbackId;
        }

        if ((Boolean)sqs.get("send-email")) {
            try {
                MandrillEmailer.sendFallbackAlarm(subject, fallbackId, ip, details);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            log.info("Email not requested.");
        }
    }
}
