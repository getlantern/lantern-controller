package org.lantern.monitoring;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.mrbean.MrBeanModule;
import org.lantern.loggly.LoggerFactory;
import org.lantern.monitoring.Stats.CounterKey;
import org.lantern.monitoring.Stats.GaugeKey;
import org.lantern.state.Mode;

public class StatshubAdapter {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOGGER = LoggerFactory
            .getLogger(StatshubAdapter.class);
    private static final StatshubAPI statshub = new StatshubAPI();

    static {
        MAPPER.registerModule(new MrBeanModule());
    }

    public static void forwardStats(
            String instanceId,
            String userGuid,
            String countryCode,
            Mode mode,
            boolean isOnline,
            boolean isFallback,
            String statsJson) {
        LOGGER.info("Forwarding stats to statshub for user: " + userGuid);
        try {
            org.lantern.Stats oldStats = MAPPER.readValue(statsJson,
                    org.lantern.Stats.class);
            Stats instanceStats = new Stats();
            if (Mode.give == mode) {
                instanceStats.setCounter(CounterKey.bytesGiven,
                        oldStats.getTotalBytesProxied());
                if (isFallback) {
                    instanceStats.setCounter(CounterKey.bytesGivenByFallback,
                            oldStats.getTotalBytesProxied());
                } else {
                    instanceStats.setCounter(CounterKey.bytesGivenByPeer,
                            oldStats.getTotalBytesProxied());
                }
                instanceStats.setGauge(
                        GaugeKey.bpsGiven,
                        oldStats.getUpBytesPerSecond()
                                + oldStats.getDownBytesPerSecond());
            } else {
                instanceStats.setCounter(CounterKey.bytesGotten,
                        oldStats.getTotalBytesProxied());
                instanceStats.setGauge(
                        GaugeKey.bpsGotten,
                        oldStats.getDownBytesPerSecond()
                                + oldStats.getUpBytesPerSecond());
            }

            Stats userStats = new Stats();
            userStats.setCounter(instanceStats.getCounter());
            userStats.setGauge(GaugeKey.online, isOnline ? 1 : 0);

            statshub.postStats(instanceId, countryCode, instanceStats);
            statshub.postStats(Stats.idForUser(userGuid), countryCode, userStats);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unable to forward stats to Statshub: "
                    + e.getMessage(), e);
        }
    }

}
