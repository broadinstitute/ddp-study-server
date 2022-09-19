package org.broadinstitute.dsm.model;

public class Util {
    public static void waitForCreationInElasticSearch() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
