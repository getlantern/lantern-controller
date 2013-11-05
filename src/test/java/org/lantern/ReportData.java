package org.lantern;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.lantern.data.Dao;
import org.lantern.data.LanternUser;

import com.google.appengine.tools.remoteapi.RemoteApiInstaller;
import com.google.appengine.tools.remoteapi.RemoteApiOptions;

public class ReportData {

    public static void main(final String[] args) {
        //String username = System.console().readLine("username: ");
        //String password = 
        //    new String(System.console().readPassword("password: "));
        RemoteApiOptions options = new RemoteApiOptions()
            .server("lanternctrl.appspot.com", 443)
            .credentials("", "");
        RemoteApiInstaller installer = new RemoteApiInstaller();
        try {
            installer.install(options);
        } catch (final IOException e) {
            e.printStackTrace();
            return;
        }
        
        final Map<String, AtomicInteger> uncensored = 
            new TreeMap<String, AtomicInteger>();
        
        final Map<String, AtomicInteger> censored = 
                new TreeMap<String, AtomicInteger>();
        
        final AtomicInteger empty = new AtomicInteger(0);
        uncensored.put("empty", empty);
        try {
            final Dao dao = new Dao();
            for (final LanternUser user : dao.getAllUsers()) {
                final Set<String> ccs = user.getCountryCodes();
                if (ccs.isEmpty()) {
                    empty.incrementAndGet();
                } else {
                    // Just use the first.
                    final String cc = ccs.iterator().next();
                    final Map<String, AtomicInteger> toUse;
                    if (CensoredUtils.isCensored(cc)) {
                        toUse = censored;
                    } else {
                        toUse = uncensored;
                    }
                    if (toUse.containsKey(cc)) {
                        toUse.get(cc).incrementAndGet();
                    } else {
                        toUse.put(cc, new AtomicInteger(1));
                    }
                }
            }
            
            print("UNCENSORED", uncensored);
            print("CENSORED", censored);

        } finally {
            installer.uninstall();
        }
    }

    private static void print(String label, Map<String, AtomicInteger> data) {
        System.out.println(label);
        for (final Entry<String, AtomicInteger> entry : data.entrySet()) {
            System.out.println(entry.getKey() +": "+entry.getValue());
        }
    }
}
