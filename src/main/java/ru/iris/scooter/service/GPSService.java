package ru.iris.scooter.service;

import de.taimos.gpsd4java.api.ObjectListener;
import de.taimos.gpsd4java.backend.GPSdEndpoint;
import de.taimos.gpsd4java.backend.ResultParser;
import de.taimos.gpsd4java.types.*;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;

/**
 * @author nix (06.04.2018)
 */

@Log4j2
public class GPSService {
    private static GPSService instance;

    @Getter
    private boolean fix = false;

    @Getter
    private volatile Integer satellites;

    @Getter
    private volatile Double latitude;

    @Getter
    private volatile Double longitude;

    @Getter
    private volatile Double speed;

    @Getter
    private volatile Double altitude;

    @Getter
    private volatile Double time;

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
                    fix = true;
                    latitude = tpv.getLatitude();
                    longitude = tpv.getLongitude();
                    speed = tpv.getSpeed();
                    altitude = tpv.getAltitude();
                    time = tpv.getTimestamp();
                } else {
                    fix = false;
                }
            }

            @Override
            public void handleSKY(final SKYObject sky) {
                satellites = sky.getSatellites().size();
                log.info("We can see {} satellites", satellites);
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
