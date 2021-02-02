package org.broadinstitute.ddp.event.dsmtask.api;

@FunctionalInterface
public interface DsmTaskProcessor {

    DsmTaskResultData processDsmTask(DsmTaskData dsmTaskData);
}
