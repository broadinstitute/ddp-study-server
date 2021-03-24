package org.broadinstitute.ddp.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.datstat.DatStatUtil;
import org.broadinstitute.ddp.datstat.ParticipantFields;
import org.broadinstitute.ddp.email.Recipient;
import org.broadinstitute.ddp.handlers.util.MedicalInfo;
import org.broadinstitute.ddp.user.BasicUser;
import org.quartz.Scheduler;

/**
 * Created by ebaker on 1/5/17.
 */
public interface MedicalInfoCollector
{
    public MedicalInfo generateMedicalInfo(Recipient recipient, DatStatUtil datStatUtil);
}
