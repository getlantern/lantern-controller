package org.lantern;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.codec.binary.Base64;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.lantern.loggly.LoggerFactory;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.google.appengine.api.utils.SystemProperty;
import com.google.apphosting.api.DeadlineExceededException;

public class SQSUtil {

    private static final String ENDPOINT
        = "sqs." + LanternConstants.AWS_REGION + ".amazonaws.com";
    private static final String REQUEST_Q_NAME;
    private static final String NOTIFY_Q_NAME;
    static {
        final String appId = SystemProperty.applicationId.get();
        REQUEST_Q_NAME = appId + "_request";
        NOTIFY_Q_NAME = "notify_" + appId;
    }

    private static String REQUEST_Q_URL;
    private static String NOTIFY_Q_URL;

    private static String getRequestQueueUrl() {
        if (REQUEST_Q_URL == null) {
            REQUEST_Q_URL = getQueueUrl(REQUEST_Q_NAME);
        }
        return REQUEST_Q_URL;
    }

    private static String getNotifyQueueUrl() {
        if (NOTIFY_Q_URL == null) {
            NOTIFY_Q_URL = getQueueUrl(NOTIFY_Q_NAME);
        }
        return NOTIFY_Q_URL;
    }

    /**
     * Return the URL of the queue with the given name, creating it if it
     * doesn't exist yet.
     */
    private static String getQueueUrl(String queueName) {
        AmazonSQSClient client = getClient();
        Integer visibilityTimeout = 3600;  // seconds
        return client.createQueue(
                new CreateQueueRequest(queueName, visibilityTimeout)
                ).getQueueUrl();
    }

    private final transient Logger log = LoggerFactory.getLogger(getClass());

    public void send(final Map<String, Object> msgMap) {
        final AmazonSQSClient sqs = getClient();
        final String msg = encode(msgMap);
        sqs.sendMessage(new SendMessageRequest(getRequestQueueUrl(), msg));
    }

    public List<Map<String, Object>> receive() {
        // The version of the AWS SDK we're using doesn't expose any method to
        // set a timeout in this request, but it returns immediately if the
        // queue is empty.
        final AmazonSQSClient sqs = getClient();
        ArrayList<Map<String, Object>> ret =
            new ArrayList<Map<String, Object>>();
        try {
            while (true) {
                ReceiveMessageRequest req = new ReceiveMessageRequest(
                        getNotifyQueueUrl());
                req.setMaxNumberOfMessages(10);
                ReceiveMessageResult res = sqs.receiveMessage(req);
                List<Message> messages = res.getMessages();
                if (messages.size() == 0) {
                    break;
                }
                log.info("Got a batch of " + messages.size());
                // Modern versions of the AWS SDK can delete messages in
                // batches.  Regrettably, that's not the case for the
                // version we're using.
                for (Message msg : messages) {
                    try {
                        DeleteMessageRequest dmreq = new DeleteMessageRequest(
                                getNotifyQueueUrl(), msg.getReceiptHandle());
                        sqs.deleteMessage(dmreq);
                        // Only act on messages we have successfully deleted,
                        // because other instances may have handled the rest,
                        // or either them or I may be able to do so in the
                        // future (e.g. if there are temporary connectivity
                        // problems).
                        Map<String, Object> map = decode(msg.getBody());
                        if (map != null) {
                            ret.add(map);
                        }
                    } catch (AmazonClientException e) {
                        log.warning("Trying to delete message: " + e);
                    }
                }
            }
        } catch (DeadlineExceededException e) {
            log.warning("Timed out waiting for SQS messages.");
        }
        return ret;
    }

    private static AmazonSQSClient getClient() {
        AmazonSQSClient ret = new AmazonSQSClient(
                LanternControllerConstants.AWS_CREDENTIALS);
        ret.setEndpoint(ENDPOINT);
        return ret;
    }

    private String encode(final Map<String, Object> map) {
        final String json = JsonUtils.jsonify(map);
        try {
            return new String(
                    Base64.encodeBase64(json.getBytes("UTF-8")),
                    "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, Object> decode(String raw) {
        try {
            final byte[] jsonBytes = Base64.decodeBase64(raw.getBytes("UTF-8"));
            final String json = new String(jsonBytes, "UTF-8");
            log.info("Got JSON SQS message: " + json);
            final ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, Map.class);
        } catch (final UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        } catch (final JsonParseException e) {
            log.severe("Error parsing SQS message: " + e.getMessage());
            return null;
        } catch (final JsonMappingException e) {
            log.severe("Error parsing SQS message: " + e.getMessage());
            return null;
        } catch (final IOException e) {
            log.severe("Error parsing SQS message: " + e.getMessage());
            return null;
        }
    }
}
