package org.lantern.data;
/* Copyright (c) 2009 Google Inc.
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


import javax.jdo.PersistenceManager;

/**
 * Finds or creates a sharded counter with the desired name.
 */
public class CounterFactory {

    /**
     * Creates the sharded counter if it does not yet exist.
     */
    public ShardedCounter getOrCreateCounter(final String name) {
        final CounterFactory factory = new CounterFactory();
        ShardedCounter counter = factory.getCounter(name);
        if (counter == null) {
            counter = factory.createCounter(name);
            counter.addShard();
        }
        return counter;
    }
    
    public ShardedCounter getCounter(final String name) {
        final ShardedCounter counter = new ShardedCounter(name);
        if (counter.isInDatastore()) {
            return counter;
        } else {
            return null;
        }
    }
    

    public ShardedCounter createCounter(final String name) {
        final ShardedCounter counter = new ShardedCounter(name);

        final DatastoreCounter counterEntity = new DatastoreCounter(name, 0);
        final PersistenceManager pm = PMF.get().getPersistenceManager();
        try {
            pm.makePersistent(counterEntity);
        } finally {
            pm.close();
        }

        return counter;
    }
}