package org.broadinstitute.lddp.util;

import org.broadinstitute.lddp.datstat.DatStatUtil;
import org.broadinstitute.lddp.email.Recipient;
import org.broadinstitute.lddp.handlers.util.MedicalInfo;

/**
 * Created by ebaker on 1/5/17.
 */
public interface MedicalInfoCollector
{
    public MedicalInfo generateMedicalInfo(Recipient recipient, DatStatUtil datStatUtil);
}
