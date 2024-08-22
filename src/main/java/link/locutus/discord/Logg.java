package link.locutus.discord;


import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.async.AsyncLoggerContextSelector;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.builder.api.*;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;

public class Logg {
    private static final Logger LOGGER;

    static {
        LOGGER = LogManager.getLogger(Logg.class.getSimpleName());
    }
    private static final boolean DEBUG = true;

    public static void text(Object obj) {
//        System.out.println(obj);
        LOGGER.error(obj);
    }

    public static void text(String msg, Object... obj) {
//        System.out.println(String.format(msg, obj));
        LOGGER.error(msg, obj);
    }

    public static void setInfoLogging() {
//        org.apache.log4j.Logger.getRootLogger().setLevel(Level.INFO);
//        Configurator.setRootLevel(org.apache.logging.log4j.Level.INFO);
        System.setProperty("Log4jContextSelector", AsyncLoggerContextSelector.class.getName());
        System.setProperty("log4j2.enable.threadlocals", "true"); // Enable garbage-free logging
        System.setProperty("log4j2.contextSelector", "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector");

        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
        loggerConfig.setLevel(Level.INFO);

        ctx.updateLoggers();
//        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
//        root.setLevel(ch.qos.logback.classic.Level.INFO);
    }
}