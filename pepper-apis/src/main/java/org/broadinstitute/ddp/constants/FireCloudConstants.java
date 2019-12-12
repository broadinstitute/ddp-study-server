package org.broadinstitute.ddp.constants;

public class FireCloudConstants {
    /*
    NOTE - application.conf's threadTimeout value controls how much time any single API request has before it's
        terminated by spark. Currently set to 30 seconds, which is beyond three-retries-of-5-seconds-each, so it
        doesn't matter, but if you find yourself cranking up the firecloud timeout, at some point spark's thread
        timeout will clobber this.
     */
    public static final int MAX_FC_TRIES = 3;
    public static final int SEC_BETWEEN_FC_TRIES = 5;
}
