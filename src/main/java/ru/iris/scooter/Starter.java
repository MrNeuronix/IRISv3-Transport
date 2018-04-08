package ru.iris.scooter;

import lombok.extern.slf4j.Slf4j;
import ru.iris.models.bus.transport.GPSDataEvent;
import ru.iris.scooter.service.ConfigService;
import ru.iris.scooter.service.GPIOService;
import ru.iris.scooter.service.GPSService;
import ru.iris.scooter.service.WSClientService;

/**
 * @author nix (06.04.2018)
 */

@Slf4j
public class Starter {

    public static void main(String[] args) throws InterruptedException {
        log.info("Starting transport info sender");

        ConfigService configService = ConfigService.getInstance();

        GPIOService gpio = GPIOService.getInstance();
        gpio.on(GPIOService.LED.MAIN, GPIOService.Color.RED);

        log.info("Starting GPS service");
        GPSService gps = GPSService.getInstance();

        WSClientService ws = WSClientService.getInstance();

        ws.connect();

        while (!GPSService.isInitialized()) {
            log.info("Waiting for GPS init done");
            Thread.sleep(1000L);
        }

        log.info("OK. IRIS connector is launched!");

        while (!gps.isFix()) {
            log.info("Waiting for GPS FIX");
            Thread.sleep(1000L);
        }

        while (true) {
            double latitude = gps.getLatitude();
            double longitude = gps.getLongitude();
            double speed = gps.getSpeed();

            if(gps.isFix()) {
                log.info("Lat: {}, Lon: {}, Speed: {}", latitude, longitude, speed);
                ws.send(GPSDataEvent.builder()
                        .latitude(latitude)
                        .longitude(longitude)
                        .speed(speed)
                        .id(Integer.valueOf(configService.get("transport.id")))
                        .build()
                );
            }

            Thread.sleep(5000L);
        }
    }
}
