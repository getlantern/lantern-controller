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

import java.io.Serializable;

public class DatastoreCounter implements Serializable {
    private static final long serialVersionUID = 8683909836035053816L;

    private final String counterName;

    // these are memcache shards
    private int shards = 1;

    private long count = 0;

    private boolean timed = false;

    public DatastoreCounter(String counterName, boolean timed) {
        this.counterName = counterName;
        this.timed = timed;
    }

    public DatastoreCounter(String counterName) {
        this(counterName, false);
    }

    public String getCounterName() {
        return counterName;
    }

    public long getCount() {
        return count;
    }

    public int getShardCount() {
        return shards;
    }

    public void setShardCount(int count) {
        shards = count;
    }

    public void increment(long count) {
        this.count += count;
    }

    public boolean isTimed() {
        return timed;
    }

    public void setTimed(boolean timed) {
        this.timed = timed;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public void addShard() {
       shards += 1;
    }

    public void addShards(int n) {
       shards += n;
    }
}
