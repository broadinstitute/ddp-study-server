package org.broadinstitute.ddp.model.activity.types;

public enum DsmNotificationEventType {
    /**
     * Message sent by DSM when saliva kit has been returned to Broad
     */
    SALIVA_RECEIVED,
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
}
