package org.lantern.data;

public interface TimedCounter {

    public abstract long getPerSecond();

    public abstract void increment(long count);

}