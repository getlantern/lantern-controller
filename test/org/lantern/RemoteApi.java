package org.lantern;

import java.io.IOException;

import org.lantern.data.Dao;

import com.google.appengine.tools.remoteapi.RemoteApiInstaller;
import com.google.appengine.tools.remoteapi.RemoteApiOptions;

public class RemoteApi {

    public static void main(final String[] args) {
        RemoteApiOptions options = new RemoteApiOptions()
            .server("lanternctrl.appspot.com", 443)
            .credentials("euccastro", "ebidentementesoneu");
        RemoteApiInstaller installer = new RemoteApiInstaller();
        try {
            installer.install(options);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return;
        }

        try {
            final Dao dao = new Dao();
            dao.forgetEveryoneSignedIn();
            /* Trigger your hacks here.*/
            //dao.createInitialUser("insertmyaccount@gmail.com");
        } finally {
            installer.uninstall();
        }


        /*
        try {
            DatastoreService ds = DatastoreServiceFactory.getDatastoreService();
            System.out.println("Key of new entity is " +
                ds.put(new Entity("Hello Remote API!")));
        } finally {
            installer.uninstall();
        }
        */
    }
}
