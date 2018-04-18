package ru.iris.scooter;

import com.pi4j.gpio.extension.ads.ADS1115GpioProvider;
import com.pi4j.io.gpio.event.GpioPinListenerAnalog;
import lombok.extern.log4j.Log4j2;
import ru.iris.models.bus.transport.BatteryDataEvent;
import ru.iris.models.bus.transport.GPSDataEvent;
import ru.iris.models.bus.transport.TransportPingEvent;
import ru.iris.scooter.service.ConfigService;
import ru.iris.scooter.service.GPIOService;
import ru.iris.scooter.service.GPSService;
import ru.iris.scooter.service.WSClientService;

import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDateTime;

/**
 * @author nix (06.04.2018)
 */

@Log4j2
public class Starter {
    private static final DecimalFormat df = new DecimalFormat("#.##");
    private static final double dividerMultiplier = 14.33;

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

        gpio.getVoltageInput().addListener((GpioPinListenerAnalog) event -> {
            double value = event.getValue();
            double percent = ((value * 105.5) / ADS1115GpioProvider.ADS1115_RANGE_MAX_VALUE);
            double voltage = gpio.getGpioVoltage().getProgrammableGainAmplifier(event.getPin()).getVoltage() * (percent / 100);
            double voltageBattery = voltage * dividerMultiplier; // value * dividerMultiplier * 4.096 / 32768

            log.info("Battery voltage: {}, gain: {}", df.format(voltageBattery), voltage);

            ws.send(BatteryDataEvent.builder()
                    .id(Integer.parseInt(configService.get("transport.id")))
                    .voltage(Double.valueOf(df.format(voltageBattery)))
                    .build()
            );
        });

        log.info("OK. IRIS connector is launched!");

        while (true) {
            double latitude = gps.getLatitude();
            double longitude = gps.getLongitude();
            double speed = gps.getSpeed();
            double elevation = gps.getAltitude();
            LocalDateTime time = gps.getTime();

            ws.send(TransportPingEvent.builder()
                    .id(Integer.valueOf(configService.get("transport.id")))
                    .build()
            );

            if(gps.isFix()) {
                log.info("Lat: {}, Lon: {}, Speed: {}, Elevation: {}", latitude, longitude, speed, elevation);

                ws.send(GPSDataEvent.builder()
                        .latitude(latitude)
                        .longitude(longitude)
                        .speed(speed)
                        .elevation(elevation)
                        .time(Instant.from(time))
                        .satellites(gps.getSatellites())
                        .id(Integer.valueOf(configService.get("transport.id")))
                        .build()
                );
            } else {
                gpio.pulse(GPIOService.LED.GPS, GPIOService.Color.RED, 200L);
            }

            Thread.sleep(1000L);
        }
    }
}
