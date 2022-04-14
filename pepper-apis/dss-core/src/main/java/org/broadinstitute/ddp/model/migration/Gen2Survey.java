package org.broadinstitute.ddp.model.migration;

public interface Gen2Survey {

    Integer getDatstatSubmissionid();

    String getDatstatSessionid();

    String getDdpCreated();

    String getDdpFirstcompleted();

    String getDdpLastupdated();

    Integer getDatstatSubmissionstatus();
}
