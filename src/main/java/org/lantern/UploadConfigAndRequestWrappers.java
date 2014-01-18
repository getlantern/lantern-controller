package org.lantern;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.util.logging.Logger;
import java.util.Map;
import java.util.HashMap;
import java.security.SecureRandom;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;

import org.lantern.data.Dao;


@SuppressWarnings("serial")
public class UploadConfigAndRequestWrappers extends HttpServlet {

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

    private static final String ENDPOINT
        = "s3-" + LanternControllerConstants.AWS_REGION + ".amazonaws.com";

    private final Dao dao = new Dao();

    private static final transient Logger log = Logger
            .getLogger(UploadConfigAndRequestWrappers.class.getName());

    @Override
    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response) {
        String userId = request.getParameter("userId");
        log.info("Uploading config for " + userId);
        String configFolder = generateConfigFolder();
        dao.setConfigFolder(userId, configFolder);
        log.info("ConfigFolder starts with '"
                 + configFolder.substring(0, 10)
                 + "'...");
        String config = compileConfig(userId);
        log.info("Uploading config:\n" + config);
        uploadToS3(configFolder, config);
        log.info("Successfully uploaded; enquequing wrapper request...");
        enqueueWrapperUploadRequest(configFolder);
        log.info("All done.");
        LanternControllerUtils.populateOKResponse(response, "OK");
    }

    private String generateConfigFolder() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        int alphabetLength = CONFIG_FOLDER_ALPHABET.length();
        for (int i=0; i < CONFIG_FOLDER_LENGTH; i++) {
            sb.append(CONFIG_FOLDER_ALPHABET.charAt(
                           random.nextInt(alphabetLength)));
        }
        return sb.toString();
    }

    private String compileConfig(String userId) {
        return "{ \"serial_no\": 1, \"fallbacks\" : [ "
            + new Dao().ofy().get(dao.findUser(userId).getFallbackProxy()).getAccessData()
            + " ] }";
    }

    private void uploadToS3(String folderName, String configContents) {
        AmazonS3Client s3client
            = new AmazonS3Client(LanternControllerConstants.AWS_CREDENTIALS);
        s3client.setEndpoint(ENDPOINT);
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

    private void enqueueWrapperUploadRequest(String folderName) {
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("upload-wrappers-to", folderName);
        new SQSUtil().send(m);
    }
}
