package org.lantern;

import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.lantern.data.Dao;

@SuppressWarnings("serial")
public class UploadConfigAndRequestWrappers extends HttpServlet {


    private static final transient Logger log = Logger
            .getLogger(UploadConfigAndRequestWrappers.class.getName());

    @Override
    public void doPost(final HttpServletRequest request,
                       final HttpServletResponse response) {
        Dao dao = new Dao();
        String userId = request.getParameter("userId");
        log.info("Uploading config for " + userId);
        String configFolder = S3Config.generateConfigFolder();
        dao.setConfigFolder(userId, configFolder);
        log.info("ConfigFolder starts with '"
                 + configFolder.substring(0, 10)
                 + "'...");
        String config = S3Config.compileConfig(userId);
        log.info("Uploading config:\n" + config);
        S3Config.uploadConfig(configFolder, config);
        log.info("Successfully uploaded; enquequing wrapper request...");
        S3Config.enqueueWrapperUploadRequest(userId, configFolder);
        log.info("All done.");
        LanternControllerUtils.populateOKResponse(response, "OK");
    }
}
