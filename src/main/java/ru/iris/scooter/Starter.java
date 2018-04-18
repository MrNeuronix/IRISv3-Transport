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

/**
 * @author nix (06.04.2018)
 */

@Log4j2
public class Starter {
    private final DecimalFormat df = new DecimalFormat("#.##");
    private final double dividerMultiplier = 14.33;
    private ConfigService configService = ConfigService.getInstance();
    private GPIOService gpio = GPIOService.getInstance();

    public static void main(String[] args) throws InterruptedException {
        new Starter().run();
    }

    private void run() throws InterruptedException {
        log.info("Starting transport info sender");

        gpio.on(GPIOService.LED.MAIN, GPIOService.Color.RED);

        log.info("Starting GPS service");
        GPSService gps = GPSService.getInstance();

        WSClientService ws = WSClientService.getInstance();

        ws.connect();

        gpio.getVoltageInput().addListener((GpioPinListenerAnalog) event -> {
            double value = event.getValue();
            double percent = ((value * 105.5) / ADS1115GpioProvider.ADS1115_RANGE_MAX_VALUE);
            double voltage = gpio.getGpioVoltage().getProgrammableGainAmplifier(event.getPin()).getVoltage() * (percent / 100);
            double voltageBattery = (voltage * dividerMultiplier) - 1; // value * dividerMultiplier * 4.096 / 32768

            log.info("Battery voltage: {}, gain: {}", df.format(voltageBattery), voltage);

            ws.send(BatteryDataEvent.builder()
                    .id(Integer.parseInt(configService.get("transport.id")))
                    .voltage(Double.valueOf(df.format(voltageBattery)))
                    .build()
            );
        });

        log.info("Starting to fetch GPS data");

        while (true) {
            ws.send(TransportPingEvent.builder()
                    .id(Integer.valueOf(configService.get("transport.id")))
                    .build()
            );

            while (!gps.getData().isEmpty()) {
                GPSService.GPSData data = gps.getData().poll();

                if (data != null) {
                    Double latitude = data.getLatitude();
                    Double longitude = data.getLongitude();
                    Double speed = data.getSpeed();
                    Double elevation = data.getAltitude();
                    Long time = data.getTime();

                    log.info("Lat: {}, Lon: {}, Speed: {}, Elevation: {}", latitude, longitude, speed, elevation);

                    ws.send(GPSDataEvent.builder()
                            .latitude(latitude)
                            .longitude(longitude)
                            .speed(speed)
                            .elevation(elevation)
                            .time(time)
                            .id(Integer.valueOf(configService.get("transport.id")))
                            .build()
                    );
                }
            }

            if(gps.isFix()) {
                fastDoublePulse(GPIOService.Color.GREEN);
            } else {
                fastDoublePulse(GPIOService.Color.RED);
            }

            Thread.sleep(1000L);
        }
    }

    private void fastDoublePulse(GPIOService.Color color) throws InterruptedException {
        gpio.pulse(GPIOService.LED.GPS, color, 150L);
        Thread.sleep(250L);
        gpio.pulse(GPIOService.LED.GPS, color, 150L);
    }
}
