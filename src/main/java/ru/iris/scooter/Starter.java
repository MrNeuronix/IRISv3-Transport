package ru.iris.scooter;

import com.pi4j.gpio.extension.ads.ADS1115GpioProvider;
import com.pi4j.io.gpio.event.GpioPinListenerAnalog;
import lombok.extern.log4j.Log4j2;
import ru.iris.models.bus.transport.BatteryDataEvent;
import ru.iris.models.bus.transport.GPSDataEvent;
import ru.iris.models.bus.transport.GPXDataEvent;
import ru.iris.scooter.service.*;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDateTime;

/**
 * @author nix (06.04.2018)
 */

@Log4j2
public class Starter {
    private static final DecimalFormat df = new DecimalFormat("#.##");
    private static final double dividerMultiplier = 14.33;
    private static int noGPSCounter;

    public static void main(String[] args) throws InterruptedException, ParserConfigurationException, TransformerException, IOException {
        log.info("Starting transport info sender");

        ConfigService configService = ConfigService.getInstance();
        GPXService gpxService = GPXService.getInstance();

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

            if (ws.isConnected()) {
                ws.send(BatteryDataEvent.builder()
                        .id(Integer.parseInt(configService.get("transport.id")))
                        .voltage(Double.valueOf(df.format(voltageBattery)))
                        .build()
                );
            }
        });

        log.info("OK. IRIS connector is launched!");

        while (!gps.isFix()) {
            log.info("Waiting for GPS FIX");
            Thread.sleep(1000L);
        }

        while (true) {
            double latitude = gps.getLatitude();
            double longitude = gps.getLongitude();
            double speed = gps.getSpeed();
            double altitude = gps.getAltitude();
            LocalDateTime time = gps.getTime();

            if (ws.isConnected()) {
                for (String data : gpxService.checkNonSentData()) {
                    log.info("Try to send GPX file");
                    if (ws.isConnected()) {
                        log.info("Connection exists - sending");
                        ws.send(GPXDataEvent.builder()
                                .id(Integer.valueOf(configService.get("transport.id")))
                                .xml(data)
                                .build()
                        );
                    }
                }
            }

            if(gps.isFix()) {
                log.info("Lat: {}, Lon: {}, Speed: {}", latitude, longitude, speed);
                gpxService.addWaypoint(latitude, longitude, altitude, time);

                if (ws.isConnected()) {
                    ws.send(GPSDataEvent.builder()
                            .latitude(latitude)
                            .longitude(longitude)
                            .speed(speed)
                            .id(Integer.valueOf(configService.get("transport.id")))
                            .build()
                    );
                }

                noGPSCounter = 0;
            } else {
                noGPSCounter++;
                if (noGPSCounter > 120 && gpxService.hasGpsData()) { // if we have GPS data
                    log.info("No GPS about 10 mins, but we have unflushed GPS data. Try to send it");
                    if (ws.isConnected()) {
                        log.info("Connection exists - sending");
                        String xml = gpxService.getXMLData();
                        ws.send(GPXDataEvent.builder()
                                .id(Integer.valueOf(configService.get("transport.id")))
                                .xml(xml)
                                .build()
                        );
                    } else {
                        log.info("No connection - save GPX to file");
                        gpxService.writeFile();
                    }

                    log.info("Flushing GPS data");
                    gpxService.flushGPX();
                }
            }

            Thread.sleep(5000L);
        }
    }
}
