package ru.iris.scooter.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.exceptions.WebsocketNotConnectedException;
import org.java_websocket.handshake.ServerHandshake;
import ru.iris.models.bus.transport.TransportConnectEvent;

import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.NotYetConnectedException;

/**
 * @author nix (07.04.2018)
 */

@Slf4j
public class WSClientService {
    private static WSClientService instance;
    private ConfigService configService;
    private GPIOService gpio = GPIOService.getInstance();
    private ObjectMapper objectMapper = new ObjectMapper();
    private WebSocketClient client;

    public static synchronized WSClientService getInstance() {
        if(instance == null) {
            try {
                instance = new WSClientService();
            } catch (URISyntaxException e) {
                log.error("WS error: ", e);
            }
        }

        return instance;
    }

    private WSClientService() throws URISyntaxException {
        configService = ConfigService.getInstance();
        createClient();
    }

    private void createClient() throws URISyntaxException {
        client = new WebSocketClient( new URI( "ws://" + configService.get("ws.server") + "/transport" )) {
            @Override
            public void onMessage( String message ) {
            }

            @Override
            public void onOpen( ServerHandshake handshake ) {
                log.info("IRIS WS endpoint connected");
                gpio.on(GPIOService.LED.MAIN, GPIOService.Color.GREEN);

                // send connected event
                try {
                    send(objectMapper.writeValueAsString(new TransportConnectEvent(Integer.valueOf(configService.get("transport.id")))));
                } catch (JsonProcessingException e) {
                    log.info("error", e);
                }
            }

            @Override
            public void onClose( int code, String reason, boolean remote ) {
                log.info("IRIS WS endpoint disconnected");
                gpio.on(GPIOService.LED.MAIN, GPIOService.Color.RED);
            }

            @Override
            public void onError( Exception ex ) {
                log.info("error", ex);
                gpio.on(GPIOService.LED.MAIN, GPIOService.Color.RED);
            }

        };
    }

    public void connect() {
        try {
            boolean connected = client.connectBlocking();

            if(!connected) {
                log.error("Can't connect now. Reconnecting");
                gpio.on(GPIOService.LED.MAIN, GPIOService.Color.RED);
                Thread.sleep(2000L);
                connect();
            }

        } catch (InterruptedException e) {
            log.error("Can't connect now - interrupted. Reconnecting");
            gpio.on(GPIOService.LED.MAIN, GPIOService.Color.RED);
            connect();
        } catch (IllegalStateException e) {
            gpio.on(GPIOService.LED.MAIN, GPIOService.Color.RED);
            log.error("Recreating client");
            try {
                createClient();
            } catch (URISyntaxException ignored) {
            }
            connect();
        }
    }

    public void send(Object message) {
        try {
            client.send(objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException e) {
            log.info("error", e);
        } catch (NotYetConnectedException | WebsocketNotConnectedException e) {
            gpio.on(GPIOService.LED.MAIN, GPIOService.Color.RED);
            connect();
        }
    }
}
