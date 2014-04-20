package de.mhellmann.util;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Properties;

import static org.junit.Assert.assertTrue;


/**
 * Created with IntelliJ IDEA.
 * User: mhellman
 * Date: 13.11.13
 * Time: 20:52
 */
public class TestUtils {

    private static final Logger LOG = LoggerFactory.getLogger(TestUtils.class);
    static {
        Log4JUtils.addConsoleAppender(LOG, Level.DEBUG);
    }

    public static Properties loadPropertiesFromPackage(Class clazz, String propertiesName) throws IOException {
        Properties properties = new Properties();
        InputStream inputStream = null;
        try {
            LOG.debug("propertiesName = {}", propertiesName);
            inputStream = clazz.getResourceAsStream(propertiesName);
            properties.load(inputStream);
        } catch (IOException e) {
            LOG.error("Error loading properties: {}", propertiesName, e);
            throw e;
        } finally {
            IOUtils.closeQuietly(inputStream);
        }
        return properties;
    }

}
