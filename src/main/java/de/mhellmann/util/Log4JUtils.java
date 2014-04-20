package de.mhellmann.util;

import org.apache.log4j.*;

/**
 * - Allows adding a ConsoleAppender to given slf4j or log4j instances for debug and development purposes.
 * - Allows to programmatically change the log level on slf4j or log4j instances.
 *
 * Date: 20.04.2014.
 * 
 * @author <a href="mailto:marten.hellmann@web.de"><strong>Marten Hellmann</strong></a>
 */
public final class Log4JUtils {

    public static final String CONSOLE_APPENDER_NAME = "Consolemio";

    private static final Logger LOG = Logger.getLogger(Log4JUtils.class);

    // Setting SYSTEM_OUT_LOGGING_ENABLED = false, will remove all system outs for all Loggers !!
    public static boolean systemOutLoggingEnabled = true;

    private Log4JUtils() {}

    public static void addConsoleAppender(Class clazz) {
        if (clazz!=null) {
            addConsoleAppender(Logger.getLogger(clazz.getName()));
        }
    }
    
    public static void addConsoleAppender(String loggerName) {
        if (loggerName!=null) {
            addConsoleAppender(Logger.getLogger(loggerName));
        }
    }

    /**
     * This adds the ConsoleAppender to the Root Logger
     */
    public static void addRootLoggerConsoleAppender() {
        addConsoleAppender(Logger.getRootLogger());
    }

    public static void addConsoleAppender(org.slf4j.Logger slf4jLogger) {
        addConsoleAppender(slf4jLogger, null);
    }

    public static void addConsoleAppender(org.slf4j.Logger slf4jLogger, Level level) {
        if (slf4jLogger!=null) {
            addConsoleAppender(Logger.getLogger(slf4jLogger.getName()), level);
        }
    }

    public static void addConsoleAppender(Logger logger) {
        addConsoleAppender(logger, null);
    }

    public static void addConsoleAppender(Logger logger, Level level) {
        if (systemOutLoggingEnabled && logger != null && logger.getAppender(CONSOLE_APPENDER_NAME) == null) {
            synchronized (Log4JUtils.class) {
                if (logger.getAppender(CONSOLE_APPENDER_NAME)==null) {
                    MyConsoleAppender appender = new MyConsoleAppender();
                    logger.addAppender(appender);
                    LOG.info("LOG4J: Added ConsoleAppender for " + logger.getName() + " to enable System.out logging for development purposes.");
                    if (level!=null) {
                        logger.setLevel(level);
                    }
                }
            }
        }
    }

    public static void setConsoleLogLevel(org.slf4j.Logger slf4jLogger, Level level) {
        if (slf4jLogger!=null) {
            Logger logger = Logger.getLogger(slf4jLogger.getName());
            if (logger!=null) {
                logger.setLevel(level);
            }
        }
    }

    public static void setLogLevel(org.slf4j.Logger slf4jLogger, Level level) {
        if (slf4jLogger!=null) {
            Logger logger = Logger.getLogger(slf4jLogger.getName());
            if (logger!=null) {
                MyConsoleAppender consoleAppender = (MyConsoleAppender)logger.getAppender(CONSOLE_APPENDER_NAME);
                if (consoleAppender!=null) {
                    consoleAppender.setThreshold(level);
                }
            }
        }
    }

    private static final class MyConsoleAppender extends ConsoleAppender {
        public MyConsoleAppender() {
            // STRANGE JUNIT NEEDS Pattern AND STREAM OTHERWISE IT WON'T WORK !!!
            super(new PatternLayout("%p %t %m%n"), ConsoleAppender.SYSTEM_OUT);
            setName(CONSOLE_APPENDER_NAME);
            //setThreshold(Level.ALL);
        }
    }

}
