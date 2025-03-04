package link.locutus.discord;


import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.async.AsyncLoggerContextSelector;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.builder.api.*;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.spi.LoggerContext;

public class Logg {
    private static final Logger LOGGER;

    static {
        LOGGER = LogManager.getLogger(Logg.class.getSimpleName());
    }
    private static final boolean DEBUG = true;

    public static void text(Object obj) {
        System.out.println(":||ERROR " + obj);
        LOGGER.error(obj);
    }

    public static void text(String msg, Object... obj) {
        LOGGER.error(msg, obj);
    }

    private static volatile boolean setInfoLogging = false;

    public static void setInfoLogging() {
        if (setInfoLogging) {
            return;
        }
        setInfoLogging = true;
//        org.apache.log4j.Logger.getRootLogger().setLevel(Level.INFO);
//        Configurator.setRootLevel(org.apache.logging.log4j.Level.INFO);
        System.setProperty("Log4jContextSelector", AsyncLoggerContextSelector.class.getName());
        System.setProperty("log4j2.enable.threadlocals", "true"); // Enable garbage-free logging
        System.setProperty("log4j2.contextSelector", "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector");

        LoggerContext ctx = LogManager.getContext(false);
        if (ctx instanceof org.apache.logging.log4j.core.LoggerContext logCtx) {
            Configuration config = logCtx.getConfiguration();
            LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
            loggerConfig.setLevel(Level.INFO);
            logCtx.updateLoggers();
        }
//        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
//        root.setLevel(ch.qos.logback.classic.Level.INFO);
    }
}