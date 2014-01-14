package org.lantern.data;

/*
 * This is loosely based on Google's sharded counter code, but
 * unlike Google's code, it is affordable to run.
 *
 * Copyright (c) 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jdo.JDOObjectNotFoundException;
import javax.jdo.PersistenceManager;

import org.lantern.loggly.LoggerFactory;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Key;
import com.google.appengine.api.datastore.KeyFactory;
import com.google.appengine.api.datastore.Transaction;
import com.google.appengine.api.memcache.ErrorHandlers;
import com.google.appengine.api.memcache.MemcacheService;
import com.google.appengine.api.memcache.MemcacheServiceFactory;


public class SettingsManager {

    private static final transient Logger log = LoggerFactory
            .getLogger(SettingsManager.class);

    private static final Key SETTINGSKEY = KeyFactory.createKey(
            Settings.class.getSimpleName(), Settings.singletonKey);

    MemcacheService cache = MemcacheServiceFactory.getMemcacheService();

    public SettingsManager() {
        cache.setErrorHandler(
                ErrorHandlers.getConsistentLogAndContinue(Level.INFO));
    }

    public String get(final String name) {
        return loadSettings().get(name);
    }

    public void set(final String name, final String value) {
        DatastoreService datastore =
                DatastoreServiceFactory.getDatastoreService();
        for (int tries=10; tries > 0; --tries) {
            Transaction txn = datastore.beginTransaction();
            try {
                PersistenceManager pm = PMF.get().getPersistenceManager();
                Settings s = loadSettingsFromDatastore(pm);
                s.set(name, value);
                writeSettings(pm, s);
                pm.close();
                txn.commit();
                cache.delete("settings");
                log.info("Saved settings.");
                return;
            }  catch (ConcurrentModificationException e) {
                log.warning("Concurrent modification!");
            } finally {
                if (txn.isActive()) {
                    txn.rollback();
                }
            }
        }
        throw new RuntimeException("Too much contention for group!");
    }

    public Map<String, String> getAllSettings() {
        return loadSettings().getAllSettings();
    }

    private Settings loadSettings() {
        // try to get from cache
        Settings s = (Settings) cache.get("settings");
        if (s != null)
            return s;
        log.info("Forced to load settings from database.");
        final PersistenceManager pm =
            PMF.get().getPersistenceManager();
        s = loadSettingsFromDatastore(pm);
        pm.close();
        cache.put("settings", s);
        return s;
    }

    private Settings loadSettingsFromDatastore(PersistenceManager pm) {
        try {
            Settings s = pm.getObjectById(Settings.class, SETTINGSKEY);
            s.restore();
            return s;
        } catch (JDOObjectNotFoundException e) {
            log.warning("Did not find a settings object."
                        + " Creating a new one.");
            return new Settings();
        }
    }

    private void writeSettings(PersistenceManager pm, Settings s) {
        long now = new Date().getTime() / 1000;
        s.setLastUpdated(now);
        s.prepareForPersistence();
        pm.makePersistent(s);
    }

    public boolean getBoolean(String name) {
        return "true".equals(get(name));
    }

    public Integer getInteger(String name) {
        String value = get(name);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new RuntimeException(e);
        }
    }

}
