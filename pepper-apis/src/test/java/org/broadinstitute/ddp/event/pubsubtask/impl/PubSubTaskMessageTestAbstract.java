package org.broadinstitute.ddp.event.pubsubtask.impl;


import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.PROJECT_ID;
import static org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.PUBSUB_SUBSCRIPTION;

import com.google.gson.Gson;
import com.google.pubsub.v1.ProjectSubscriptionName;
import org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil;
import org.broadinstitute.ddp.event.pubsubtask.PubSubTaskTestUtil.TestResultSender;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskReceiver;
import org.broadinstitute.ddp.util.GsonUtil;

public class PubSubTaskMessageTestAbstract {

    protected final ProjectSubscriptionName projectSubscriptionName =
            ProjectSubscriptionName.of(PROJECT_ID, PUBSUB_SUBSCRIPTION);
    protected PubSubTaskReceiver pubSubTaskReceiver;
    protected TestResultSender testResultSender;

    protected final Gson gson = GsonUtil.standardGson();

    protected void init() {
        testResultSender = new TestResultSender();
        pubSubTaskReceiver = new PubSubTaskReceiver(projectSubscriptionName,
                new PubSubTaskTestUtil.TestTaskProcessorFactory(), testResultSender);
    }
}
