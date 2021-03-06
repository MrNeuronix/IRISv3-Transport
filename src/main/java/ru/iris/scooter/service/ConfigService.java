package ru.iris.scooter.service;

import lombok.extern.log4j.Log4j2;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * @author nix (07.04.2018)
 */

@Log4j2
public class ConfigService {
    private static ConfigService instance;
    private Properties config;

    public static synchronized ConfigService getInstance() {
        if(instance == null) {
            try {
                instance = new ConfigService();
            } catch (IOException e) {
                log.error("WS error: ", e);
            }
        }

        return instance;
    }

    private ConfigService() throws IOException {
        String rootPath = Thread.currentThread().getContextClassLoader().getResource("").getPath() + "../";
        String appConfigPath = rootPath + "app.properties";

        config = new Properties();
        config.load(new FileInputStream(appConfigPath));
    }

    public String get(String key) {
        return config.getProperty(key);
    }
}
