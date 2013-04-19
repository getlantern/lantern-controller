package org.lantern;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SendMessageRequest;

import com.google.apphosting.api.DeadlineExceededException;

import org.apache.commons.codec.binary.Base64;

import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

public class SQSUtil {

    private final String SPAWN_REQUEST_Q =
        "https://sqs.ap-southeast-1.amazonaws.com/670960738222/spawn_request";
    private final String NOTIFY_Q =
        "https://sqs.ap-southeast-1.amazonaws.com/670960738222/lanternctrl_notify";
    private final BasicAWSCredentials creds = new BasicAWSCredentials(
            LanternControllerConstants.getAWSAccessKeyId(),
            LanternControllerConstants.getAWSSecretKey());
    private final transient Logger log = Logger.getLogger(getClass().getName());

    public void send(final Map<String, Object> msgMap) {
        final AmazonSQSClient sqs = new AmazonSQSClient(creds);
        final String msg = encode(msgMap);
        SendMessageRequest req = new SendMessageRequest(SPAWN_REQUEST_Q, msg);
        sqs.sendMessage(req);
    }

    public List<Map<String, Object>> receive() {
        // The version of the AWS SDK we're using doesn't expose any method to
        // set a timeout in this request, but it returns immediately if the
        // queue is empty.
        final AmazonSQSClient sqs = new AmazonSQSClient(creds);
        ArrayList<Map<String, Object>> ret =
            new ArrayList<Map<String, Object>>();
        try {
            while (true) {
                ReceiveMessageRequest req = new ReceiveMessageRequest(
                        NOTIFY_Q);
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
                                NOTIFY_Q, msg.getReceiptHandle());
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
