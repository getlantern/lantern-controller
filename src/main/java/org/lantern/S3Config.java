package org.lantern;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.logging.Logger;
import java.security.SecureRandom;

import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.ObjectMetadata;

import org.lantern.aws.SignedURL;
import org.lantern.data.Dao;
import org.lantern.data.LanternInstance;
import org.lantern.data.LanternUser;


public class S3Config {

    private static final transient Logger log = Logger
            .getLogger(S3Config.class.getName());

    /** Minimum time clients should wait to check S3 for config updates. */
    private static final int MIN_POLL_MINUTES = 5;
    /** Maximum time clients should wait to check S3 for config updates. */
    private static final int MAX_POLL_MINUTES = 15;

    private static final String INSTALLER_BUCKET = "lantern-installers";

    /* DRY: grep lantern_aws. */
    private static final String CONFIG_BUCKET = "lantern-config";
    private static final String CONFIG_FILENAME = "config.json";

    private static final String INSTALLER_BASE_URL = "https://s3.amazonaws.com";
    private static final String LANDING_PAGE_URL
        = "https://s3.amazonaws.com/lantern-installers/index.html";
    private static final long ONE_HUNDREDISH_YEARS_IN_SECS
        = 60 * 60 * 24 * 365 * 100;

    /**
     * http://docs.aws.amazon.com/AmazonS3/latest/dev/UsingMetadata.html
     *
     * "The name for a key is a sequence of Unicode characters whose UTF-8
     * encoding is at most 1024 bytes long."
     *
     * We further constrain the length so it will fit in a Windows filename,
     * since we are storing this in the installer name.
     *
     * We reserve some space for a lantern- prefix and a file extension.
     */
    private static final int CONFIG_FOLDER_LENGTH = 255 - "lantern-.ext".length();

    /**
     * For simplicity, and because the key space is big enough anyway,
     * let's just use characters that don't need to be percent encoded in
     * a URL path.
     *
     * http://tools.ietf.org/html/rfc3986#section-2.3
     *
     * As a happy coincidence, all supported OSs allow these characters in
     * filenames.
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
        if (user.getFallbackProxy() == null) {
            throw new RuntimeException("No fallback proxy");
        }
        LanternInstance fallback = dao.ofy().get(user.getFallbackProxy());
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

    public static String getLinux32DownloadUrl(String configFolder) {
        return getDownloadUrl(configFolder, "linux32", "-32", "deb");
    }

    public static String getLinux64DownloadUrl(String configFolder) {
        return getDownloadUrl(configFolder, "linux64", "-64", "deb");
    }

    public static String getWindowsDownloadUrl(String configFolder) {
        return getDownloadUrl(configFolder, "windows", "", "exe");
    }

    public static String getOsxDownloadUrl(String configFolder) {
        return getDownloadUrl(configFolder, "osx", "", "dmg");
    }

    private static String getDownloadUrl(String configFolder,
                                         String platform,
                                         String arch,
                                         String extension) {
        String awsId
            = LanternControllerConstants.AWS_CREDENTIALS.getAWSAccessKeyId();
        String awsKey
            = LanternControllerConstants.AWS_CREDENTIALS.getAWSSecretKey();
        java.util.Date now = new java.util.Date();
        long nowSecs = Math.round(now.getTime() / 1000);
        long expiration = nowSecs + ONE_HUNDREDISH_YEARS_IN_SECS;
        String s3key = "newest" + arch + "." + extension;
        String resource = "/" + INSTALLER_BUCKET + "/" + s3key;
        String filename = "lantern-" + configFolder + "." + extension;
        String reqParam = "response-content-disposition= 'attachment; filename=" + filename;
        try {
            String url = new SignedURL(awsId,
                                       awsKey,
                                       INSTALLER_BASE_URL,
                                       resource,
                                       expiration).withQueryString(reqParam)
                                                     .signed();
            String encodedUrl = URLEncoder.encode(url, "UTF-8");
            return LANDING_PAGE_URL
                   + "?platform=" + platform
                   + "&installer=" + encodedUrl;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
