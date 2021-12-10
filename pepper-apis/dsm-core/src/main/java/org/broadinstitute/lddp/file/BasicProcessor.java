package org.broadinstitute.lddp.file;

import org.broadinstitute.lddp.datstat.SurveyInstance;

import java.io.InputStream;

public interface BasicProcessor<T extends SurveyInstance>
{
    public InputStream generateStream(T surveyInstance);

    public String getFileName();
}