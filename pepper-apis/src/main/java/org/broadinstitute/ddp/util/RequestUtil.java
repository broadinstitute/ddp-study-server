package org.broadinstitute.ddp.util; 

public class RequestUtil {
    public static String urlComponentToStringConstant(String urlComponent) {
        return urlComponent.replaceAll("-", "_").toUpperCase();
    }
}
