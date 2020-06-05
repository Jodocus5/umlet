package com.baselet.gwt.client.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GWTLogger implements CustomLogger {

    private final Logger logger;

    public GWTLogger(Class<?> clazz) {
        logger = LoggerFactory.getLogger(clazz);
    }

    @Override
    public void trace(String message) {
        logger.trace(message);
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void error(String message) {
        logger.error(message);
    }
}
