package org.lantern;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.lantern.data.Dao;

import com.google.appengine.tools.remoteapi.RemoteApiInstaller;
import com.google.appengine.tools.remoteapi.RemoteApiOptions;

/**
 * You can use this script to add a Lantern user.
 */
public class AddUser {

    public static void main(final String[] args) throws IOException {
        
        final Properties props = new Properties();
        final File creds = new File("remoteapi.properties");
        if (creds.isFile()) {
            InputStream is = null;
            try {
                is = new FileInputStream(creds);
                props.load(is);
            } finally {
             IOUtils.closeQuietly(is);
            }
        } else {
            System.err.println("PLEASE ENTER username: and password: and " +
                "optionally controller: in "+creds.getAbsolutePath());
            return;
        }
         
        final String username = props.getProperty("username");
        final String password = props.getProperty("password");
        final String stored = props.getProperty("controller");
        final String controller = 
            StringUtils.isNotBlank(stored) ? stored + ".appspot.com" : "lanternctrl.appspot.com";
        final RemoteApiOptions options = new RemoteApiOptions()
            .server(controller, 443)
            .credentials(username, password);
        final RemoteApiInstaller installer = new RemoteApiInstaller();
        try {
            installer.install(options);
        } catch (final IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return;
        }

        try {
            final Dao dao = new Dao();
            
            // SPECIFY THE INVITER HERE!
            final String inviterEmail = "aranhoide@gmail.com";
            final String fpid = dao.findUser(inviterEmail).getFallbackProxy().getName();            
            // SPECIFY THE INVITEE HERE!!
            dao.createInvitee("myles@getlantern.org", inviterEmail, fpid);
            
        } finally {
            installer.uninstall();
        }
    }
}
