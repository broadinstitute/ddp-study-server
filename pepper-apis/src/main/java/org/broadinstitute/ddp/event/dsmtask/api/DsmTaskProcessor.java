package org.broadinstitute.ddp.event.dsmtask.api;

/**
 * Interface defining a processor which should do some actions
 * in response to DsmTask (published to pubsub topic
 * "dsm-to-dss-tasks").
 */
@FunctionalInterface
public interface DsmTaskProcessor {

    DsmTaskResultData processDsmTask(DsmTaskData dsmTaskData);
}
