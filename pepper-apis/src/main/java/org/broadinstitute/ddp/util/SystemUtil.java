package org.broadinstitute.ddp.util;

/**
 * Contains helper methods to read System.properties values.
 */
public class SystemUtil {

    /**
     * Read System.property of boolean type.
     * If property not specified then return defaultValue
     * @param name - property name
     * @param defaultValue - default value (in case if property not specified)
     * @return boolean true, in case if value=='true' (case insensitive), otherwise false
     *      (even if any other value, not equals true or false)
     */
    public static boolean readSystemProperty(String name, boolean defaultValue) {
        String value = System.getProperty(name);
        return value == null ? defaultValue : Boolean.parseBoolean(value);
    }
}
