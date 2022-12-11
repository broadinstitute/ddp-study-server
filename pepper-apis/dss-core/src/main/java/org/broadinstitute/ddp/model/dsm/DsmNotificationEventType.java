package org.broadinstitute.ddp.model.dsm;

public enum DsmNotificationEventType {
    /**
     * Message sent by DSM when saliva kit has been returned to Broad
     */
    SALIVA_RECEIVED,
    /**
     * Message sent by DSM when saliva kit has been sent
     */
    SALIVA_SENT,
    /**
     * Message sent by DSM when blood kit has been returned to Broad
     */
    BLOOD_RECEIVED,
    /**
     * Message sent by DSM when blood kit has been sent
     */
    BLOOD_SENT,
    /**
     * Message sent when blood kit has not been returned to Broad within 4 weeks
     */
    BLOOD_SENT_4WK,
    /**
     * Message sent by DSM when TESTBOSTON kit is sent
     */
    TESTBOSTON_SENT,
    /**
     * Message sent by DSM when TESTBOSTON kit is delivered to participant
     */
    TESTBOSTON_DELIVERED,
    /**
     * Message sent by DSM when TESTBOSTON kit is received back
     */
    TESTBOSTON_RECEIVED,
    /**
     * Message sent by DSM when a result from a lab test on a kit is available
     */
    TEST_RESULT,
    /**
     * Message sent by DSM when a Circadia kit is delivered to participant
     */
    CIRCADIA_SENT,
    /**
     * Message sent by DSM when a Circadia  kit is received back
     */
    CIRCADIA_RECEIVED,
    /**
     * Message sent by DSM when DLMO Collection Date #1 is entered for Circadia
     */
    DLMO_DATE_1,
    /**
     * Message sent by DSM when DLMO Collection Date #2 is entered for Circadia
     */
    DLMO_DATE_2,

    /**
     * Message sent by DSM on stoolkit sent/scanned event.
     */
    STOOL_SENT,

    /**
     * Message sent by DSM on stool received event.
     */
    STOOL_RECEIVED
}
