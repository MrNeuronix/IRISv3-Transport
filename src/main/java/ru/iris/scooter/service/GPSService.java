package ru.iris.scooter.service;

import com.google.common.collect.EvictingQueue;
import de.taimos.gpsd4java.api.ObjectListener;
import de.taimos.gpsd4java.backend.GPSdEndpoint;
import de.taimos.gpsd4java.backend.ResultParser;
import de.taimos.gpsd4java.types.*;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.time.Instant;
import java.util.Queue;

/**
 * @author nix (06.04.2018)
 */

@Log4j2
public class GPSService {
    private static GPSService instance;

    @Getter
    private volatile boolean fix = false;

    @Getter
    private Queue<GPSData> data = EvictingQueue.create(10);

    @Getter
    @Builder
    public static class GPSData {
        private Double latitude;
        private Double longitude;
        private Double speed;
        private Double altitude;
        private Instant time;
    }

    public static synchronized GPSService getInstance() {
        if(instance == null) {
            instance = new GPSService();
        }
        return instance;
    }

    private GPSService() {
        ConfigService configService = ConfigService.getInstance();

        final GPSdEndpoint ep;
        try {
            ep = new GPSdEndpoint(
                    configService.get("gps.host"),
                    Integer.valueOf(configService.get("gps.port")),
                    new ResultParser());
        } catch (IOException e) {
            log.error("Can't connect to GPSd daemon", e);
            return;
        }

        ep.addListener(new ObjectListener() {
            @Override
            public void handleTPV(final TPVObject tpv) {
                if(!tpv.getMode().equals(ENMEAMode.NoFix) && !tpv.getMode().equals(ENMEAMode.NotSeen)) {
                    data.offer(
                            GPSData.builder()
                                    .latitude(tpv.getLatitude())
                                    .longitude(tpv.getLongitude())
                                    .altitude(tpv.getAltitude())
                                    .speed(tpv.getSpeed())
                                    .time(Instant.ofEpochMilli((long) tpv.getTimestamp()))
                                    .build()
                    );
                    fix = true;
                } else {
                    fix = false;
                }
            }

            @Override
            public void handleSKY(final SKYObject sky) {
                if(sky.getSatellites().size() > 0) {
                    log.info("We can see {} satellites", sky.getSatellites().size());
                }
            }
        });

        ep.start();
        try {
            ep.watch(true, true);
        } catch (IOException e) {
            log.error("Can't watch on GPSd events", e);
        }
    }
}
