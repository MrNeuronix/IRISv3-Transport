package ru.iris.scooter.service;

import com.pi4j.gpio.extension.ads.ADS1115GpioProvider;
import com.pi4j.gpio.extension.ads.ADS1115Pin;
import com.pi4j.gpio.extension.ads.ADS1x15GpioProvider;
import com.pi4j.io.gpio.*;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CFactory;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * @author nix (07.04.2018)
 */

@Slf4j
public class GPIOService {
    @Getter
    private final ADS1115GpioProvider gpioVoltage;
    private static GPIOService instance;
    @Getter
    private GpioPinAnalogInput voltageInput;
    private final Map<LED, LedState> ledState = new HashMap<>();

    @NoArgsConstructor
    @AllArgsConstructor
    private class LedState {
        @Getter @Setter
        private Color color;
        private GpioPinDigitalOutput redPin;
        private GpioPinDigitalOutput greenPin;
        private Future<?> blinkRedTask;
        private Future<?> blinkGreenTask;

        private GpioPinDigitalOutput getPinByColor(Color color) {
            if(color.equals(Color.RED)) {
                return redPin;
            } else {
                return greenPin;
            }
        }

        private Future<?> getTaskByColor(Color color) {
            if(color.equals(Color.RED)) {
                return blinkRedTask;
            } else {
                return blinkGreenTask;
            }
        }

        private void setTaskByColor(Color color, Future<?> task) {
            if(color.equals(Color.RED)) {
                blinkRedTask = task;
            } else {
                blinkGreenTask = task;
            }
        }
    }

    public enum Color{
        RED,
        GREEN,
        OFF
    }

    public enum LED {
        MAIN,
        GPS
    }

    private GPIOService() throws IOException, I2CFactory.UnsupportedBusNumberException {
        final GpioController gpio = GpioFactory.getInstance();

        GpioPinDigitalOutput led01red = gpio.provisionDigitalOutputPin(RaspiBcmPin.GPIO_29, "MAIN RED", PinState.LOW);
        GpioPinDigitalOutput led02red = gpio.provisionDigitalOutputPin(RaspiBcmPin.GPIO_27, "MAIN RED", PinState.LOW);
        GpioPinDigitalOutput led01green = gpio.provisionDigitalOutputPin(RaspiBcmPin.GPIO_28, "GPS GREEN", PinState.LOW);
        GpioPinDigitalOutput led02green = gpio.provisionDigitalOutputPin(RaspiBcmPin.GPIO_25, "GPS GREEN", PinState.LOW);

        led01red.setShutdownOptions(true, PinState.LOW);
        led01green.setShutdownOptions(true, PinState.LOW);
        led02red.setShutdownOptions(true, PinState.LOW);
        led02green.setShutdownOptions(true, PinState.LOW);

        ledState.put(LED.MAIN, new LedState(Color.OFF, led01red, led01green, null, null));
        ledState.put(LED.GPS, new LedState(Color.OFF, led02red, led02green, null, null));

        gpioVoltage = new ADS1115GpioProvider(I2CBus.BUS_1, ADS1115GpioProvider.ADS1115_ADDRESS_0x4A);
        voltageInput = gpio.provisionAnalogInputPin(gpioVoltage, ADS1115Pin.INPUT_A0, "Voltage-A0");
        gpioVoltage.setProgrammableGainAmplifier(ADS1x15GpioProvider.ProgrammableGainAmplifierValue.PGA_4_096V, ADS1115Pin.ALL);
        gpioVoltage.setEventThreshold(50, ADS1115Pin.ALL);
        gpioVoltage.setMonitorInterval(1000);
    }

    public static synchronized GPIOService getInstance() {
        if(instance == null) {
            try {
                instance = new GPIOService();
            } catch (I2CFactory.UnsupportedBusNumberException | IOException e) {
                log.error("GPIO voltage error!");
            }
        }
        return instance;
    }

    public void toggle(LED led, Color color) {
        LedState state = ledState.get(led);
        cancelBlink(state);

        if(!color.equals(state.color) && !state.color.equals(Color.OFF)) {
            GpioPinDigitalOutput pin = state.getPinByColor(state.color);
            pin.low();
        }

        state.setColor(color);
        state.getPinByColor(color).toggle();
    }

    public void on(LED led, Color color) {
        LedState state = ledState.get(led);
        cancelBlink(state);

        if(!color.equals(state.color) && !state.color.equals(Color.OFF)) {
            GpioPinDigitalOutput pin = state.getPinByColor(state.color);
            pin.low();
        }

        state.setColor(color);
        state.getPinByColor(color).high();
    }

    public void off(LED led, Color color) {
        LedState state = ledState.get(led);
        cancelBlink(state);

        if(!color.equals(state.color) && !state.color.equals(Color.OFF)) {
            GpioPinDigitalOutput pin = state.getPinByColor(state.color);
            pin.low();
        }

        state.setColor(Color.OFF);
        state.getPinByColor(color).low();
    }

    public void blink(LED led, Color color, long delay) {
        LedState state = ledState.get(led);

        if(!color.equals(state.color) && !state.color.equals(Color.OFF)) {
            GpioPinDigitalOutput pin = state.getPinByColor(state.color);
            pin.low();
        }

        cancelBlink(state);
        Future<?> task = state.getPinByColor(color).blink(delay);
        state.setColor(color);
        state.setTaskByColor(color, task);
    }

    public void pulse(LED led, Color color, long duration) {
        LedState state = ledState.get(led);
        cancelBlink(state);

        if(!color.equals(state.color) && !state.color.equals(Color.OFF)) {
            GpioPinDigitalOutput pin = state.getPinByColor(state.color);
            pin.low();
        }

        state.setColor(color);
        state.getPinByColor(color).pulse(duration);
    }

    private void cancelBlink(LedState state) {
        if(state.getTaskByColor(Color.RED) != null) {
            state.getTaskByColor(Color.RED).cancel(true);
            state.getPinByColor(Color.RED).blink(0);
            state.setTaskByColor(Color.RED, null);
        }
        if(state.getTaskByColor(Color.GREEN) != null) {
            state.getTaskByColor(Color.GREEN).cancel(true);
            state.getPinByColor(Color.GREEN).blink(0);
            state.setTaskByColor(Color.GREEN, null);
        }
    }
}
