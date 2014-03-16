package org.lantern.monitoring;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.mrbean.MrBeanModule;
import org.lantern.loggly.LoggerFactory;
import org.lantern.monitoring.Stats.Counters;
import org.lantern.monitoring.Stats.Gauges;
import org.lantern.monitoring.Stats.Members;
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
                instanceStats.setCounter(Counters.bytesGiven,
                        oldStats.getTotalBytesProxied());
                if (isFallback) {
                    instanceStats.setCounter(Counters.bytesGivenByFallback,
                            oldStats.getTotalBytesProxied());
                } else {
                    instanceStats.setCounter(Counters.bytesGivenByPeer,
                            oldStats.getTotalBytesProxied());
                }
                instanceStats.setGauge(
                        Gauges.bpsGiven,
                        oldStats.getUpBytesPerSecond()
                                + oldStats.getDownBytesPerSecond());
            } else {
                instanceStats.setCounter(Counters.bytesGotten,
                        oldStats.getTotalBytesProxied());
                instanceStats.setGauge(
                        Gauges.bpsGotten,
                        oldStats.getDownBytesPerSecond()
                                + oldStats.getUpBytesPerSecond());
            }
            statshub.postInstanceStats(
                    instanceId,
                    userGuid,
                    countryCode,
                    isFallback,
                    instanceStats);

            Stats userStats = new Stats();
            userStats.setGauge(Gauges.online, isOnline ? 1 : 0);
            if (isOnline) {
                userStats.setMember(Members.everOnline, userGuid);
            }

            statshub.postUserStats(userGuid, countryCode, userStats);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unable to forward stats to Statshub: "
                    + e.getMessage(), e);
        }
    }

}
