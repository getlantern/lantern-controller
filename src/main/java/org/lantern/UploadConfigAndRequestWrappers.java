package org.lantern;

import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@SuppressWarnings("serial")
public class UploadConfigAndRequestWrappers extends HttpServlet {


    private static final transient Logger log = Logger
            .getLogger(UploadConfigAndRequestWrappers.class.getName());

    @Override
    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response) {
        log.severe("Received call to upload config and request wrappers, " +
            "but we're not using that anymore");
        /*
        Dao dao = new Dao();
        String userId = request.getParameter("userId");
        log.info("Uploading config for " + userId);
        String configFolder = S3ConfigUtil.generateConfigFolder();
        dao.setConfigFolder(userId, configFolder);
        log.info("ConfigFolder starts with '"
                 + configFolder.substring(0, 10)
                 + "'...");
        String config = S3ConfigUtil.compileConfig(userId);
        log.info("Uploading config:\n" + config);
        S3ConfigUtil.uploadConfig(configFolder, config);
        log.info("Successfully uploaded; enquequing wrapper request...");
        S3ConfigUtil.enqueueWrapperUploadRequest(userId, configFolder);
        log.info("All done.");
        LanternControllerUtils.populateOKResponse(response, "OK");
        */
    }
}
