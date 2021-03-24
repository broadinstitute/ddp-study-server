package org.broadinstitute.ddp.file;

import org.broadinstitute.ddp.datstat.SurveyInstance;

import java.io.InputStream;

public interface BasicProcessor<T extends SurveyInstance>
{
    public InputStream generateStream(T surveyInstance);

    public String getFileName();
}