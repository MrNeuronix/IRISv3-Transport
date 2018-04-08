package ru.iris.scooter.service;

import com.ivkos.gpsd4j.client.GpsdClient;
import com.ivkos.gpsd4j.client.GpsdClientOptions;
import com.ivkos.gpsd4j.messages.DeviceMessage;
import com.ivkos.gpsd4j.messages.enums.NMEAMode;
import com.ivkos.gpsd4j.messages.reports.SKYReport;
import com.ivkos.gpsd4j.messages.reports.TPVReport;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author nix (06.04.2018)
 */

@Slf4j
public class GPSService {
    private static GPSService instance;
    private ConfigService configService;
    private GpsdClient client;
    private GPIOService gpio;

    @Getter
    private static boolean initialized = false;

    @Getter
    private boolean fix = false;

    @Getter
    private boolean error = false;

    @Getter
    private int satellites;

    @Getter
    private double latitude;

    @Getter
    private double longitude;

    @Getter
    private double speed;

    public static synchronized GPSService getInstance() {
        if(instance == null) {
            instance = new GPSService();
        }
        return instance;
    }

    private GPSService() {
        configService = ConfigService.getInstance();

        gpio = GPIOService.getInstance();
        gpio.blink(GPIOService.LED.GPS, GPIOService.Color.RED, 500L);

        GpsdClientOptions options = new GpsdClientOptions()
                .setReconnectOnDisconnect(true)
                .setConnectTimeout(3000) // ms
                .setIdleTimeout(30) // seconds
                .setReconnectAttempts(10)
                .setReconnectInterval(5000); // ms

        client = new GpsdClient(configService.get("gps.host"), Integer.valueOf(configService.get("gps.port")), options)
                .setSuccessfulConnectionHandler(client -> {
                    log.info("GPS initialized");
                    initialized = true;

                    DeviceMessage device = new DeviceMessage();
                    device.setPath(configService.get("gps.device.path"));
                    device.setNative(true);

                    client.sendCommand(device);
                    client.watch();
                })
                .addHandler(TPVReport.class, tpv -> {
                    if(!tpv.getMode().equals(NMEAMode.NoFix) && !tpv.getMode().equals(NMEAMode.NotSet)) {
                        fix = true;
                        latitude = tpv.getLatitude();
                        longitude = tpv.getLongitude();
                        speed = tpv.getSpeed();

                        gpio.pulse(GPIOService.LED.GPS, GPIOService.Color.GREEN, 200L);
                    } else {
                        fix = false;
                    }
                })
                .addHandler(SKYReport.class, sky -> {
                    satellites = sky.getSatellites().size();
                    log.info("We can see {} satellites\n", satellites);
                })
                .addErrorHandler(err -> {
                    log.error(err.getMessage());
                    fix = false;
                    error = true;
                    satellites = 0;
                    gpio.on(GPIOService.LED.GPS, GPIOService.Color.RED);
                })
                .start();
    }
}
