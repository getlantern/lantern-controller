package org.lantern;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.util.logging.Logger;
import java.util.Map;
import java.util.HashMap;
import java.security.SecureRandom;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;

import org.lantern.data.Dao;
import org.lantern.data.FallbackProxy;
import org.lantern.data.LanternUser;


public class S3Config {

    private static final transient Logger log = Logger
            .getLogger(S3Config.class.getName());

    /** Minimum time clients should wait to check S3 for config updates. */
    private static final int MIN_POLL_MINUTES = 5;
    /** Maximum time clients should wait to check S3 for config updates. */
    private static final int MAX_POLL_MINUTES = 15;

    /* DRY: grep lantern_aws. */
    private static final String CONFIG_BUCKET = "lantern-config";
    private static final String CONFIG_FILENAME = "config.json";

    /**
     * http://docs.aws.amazon.com/AmazonS3/latest/dev/UsingMetadata.html
     *
     * "The name for a key is a sequence of Unicode characters whose UTF-8
     * encoding is at most 1024 bytes long."
     *
     * We reserve some 64 characters for filenames (the longest named wrappers
     * currently have ~40 chars).
     */
    private static final int CONFIG_FOLDER_LENGTH = 1024 - 64;

    /**
     * For simplicity, and because the key space is absurdly vast anyway,
     * let's just use characters that don't need to be percent encoded in
     * a URL path.
     *
     * http://tools.ietf.org/html/rfc3986#section-2.3
     */
    private static final String CONFIG_FOLDER_ALPHABET
        = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._~";

    private static final Dao dao = new Dao();

    /**
     * Utility.
     *
     * *WARNING*: If you call this from RemoteApi make sure to hardcode
     * LanternControllerConstants.CONTROLLER_ID.  For some reason that gets
     * initialized to null by default in a RemoteApi context.
     */
    public static void refreshConfig(String userId) {
        log.info("Refreshing config for " + userId);
        String config = compileConfig(userId);
        LanternUser user = dao.findUser(userId);
        if (user.getConfigFolder() == null) {
            throw new RuntimeException("No config folder for user " + userId);
        }
        uploadConfig(user.getConfigFolder(), config);
    }

    /**
     * Utility.
     *
     * *WARNING*: If you call this from RemoteApi make sure to hardcode
     * LanternControllerConstants.CONTROLLER_ID.  For some reason that gets
     * initialized to null by default in a RemoteApi context.
     */
    public static void refreshAllConfigs() {
        for (LanternUser user : dao.ofy().query(LanternUser.class)) {
            try {
                refreshConfig(user.getId());
            } catch (Exception e) {
                // This will happen for the root user.
                log.warning("Exception trying to refresh config: " + e);
            }
        }
    }

    /**
     * Utility.
     *
     * *WARNING*: If you call this from RemoteApi make sure to hardcode
     * LanternControllerConstants.CONTROLLER_ID.  For some reason that gets
     * initialized to null by default in a RemoteApi context.
     */
    public static void refreshWrapper(String userId) {
        log.info("Refreshing wrapper for " + userId);
        LanternUser user = dao.findUser(userId);
        if (user.getConfigFolder() == null) {
            throw new RuntimeException("No config folder for user " + userId);
        }
        enqueueWrapperUploadRequest(user.getId(), user.getConfigFolder());
    }

    public static String generateConfigFolder() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        int alphabetLength = CONFIG_FOLDER_ALPHABET.length();
        for (int i=0; i < CONFIG_FOLDER_LENGTH; i++) {
            sb.append(CONFIG_FOLDER_ALPHABET.charAt(
                           random.nextInt(alphabetLength)));
        }
        return sb.toString();
    }

    public static String compileConfig(String userId) {
        LanternUser user = dao.findUser(userId);
        if (user == null) {
            throw new RuntimeException("User doesn't exist");
        }
        if (user.getFallback() == null) {
            throw new RuntimeException("No fallback proxy");
        }
        FallbackProxy fallback = dao.ofy().get(user.getFallback());
        if (fallback == null) {
           throw new RuntimeException("Fallback not found");
        }
        String accessData = fallback.getAccessData();
        if (accessData == null) {
            throw new RuntimeException("Fallback has no access data");
        }
        return "{ \"serial_no\": 2"
            + ", \"controller\": \""
                + LanternControllerConstants.CONTROLLER_ID + "\""
            + ", \"minpoll\": " + MIN_POLL_MINUTES
            + ", \"maxpoll\": " + MAX_POLL_MINUTES
            + ", \"fallbacks\" : [ "
                + accessData
            + " ] }";
    }

    public static void uploadConfig(String folderName, String configContents) {
        AmazonS3Client s3client
            = new AmazonS3Client(LanternControllerConstants.AWS_CREDENTIALS);
        s3client.setEndpoint(LanternConstants.S3_ENDPOINT);
        String keyName = folderName + "/" + CONFIG_FILENAME;
        ObjectMetadata md = new ObjectMetadata();
        md.setCacheControl("no-cache");
        md.setContentDisposition("attachment; filename=\""
                                 + CONFIG_FILENAME
                                 + "\"");
        md.setContentType("application/json");
        try {
            s3client.putObject(CONFIG_BUCKET,
                               keyName,
                               new ByteArrayInputStream(
                                   configContents.getBytes("UTF-8")),
                               md);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static void enqueueWrapperUploadRequest(String userId,
                                             String folderName) {
        log.info("Requesting wrappers for " + userId);
        Map<String, Object> m = new HashMap<String, Object>();
        //DRY: cloudmaster.py and upload_wrappers.py in lantern_aws
        m.put("upload-wrappers-id", userId);
        m.put("upload-wrappers-to", folderName);
        new SQSUtil().send(m);
    }
}
