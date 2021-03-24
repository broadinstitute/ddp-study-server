package org.broadinstitute.ddp.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.datstat.ParticipantFields;
import org.broadinstitute.ddp.user.BasicUser;
import org.quartz.Scheduler;

/**
 * Created by ebaker on 1/5/17.
 */
public interface EDCClient
{
    public void startup(Config config, Scheduler scheduler);

    public boolean participantExists(String email);

    public String addParticipant(BasicUser participant, String participantListName);

    public void sendEmailToParticipant(String email);

    public String generateParticipantFirstSurveyUrl(String id);

    public String generateAnonSurveyUrl(String surveyName);

    public JsonArray getAllParticipants(ParticipantFields[] participantFields);

    public JsonElement getParticipantById(String id, ParticipantFields[] participantFields);

    public JsonArray getKitRequestsDetails();

    public JsonArray getKitRequestsDetails(String UUID);

    public String getInstitutionRequests(int lastId);

    public String getParticipantInstitutionInfo();

    public String getParticipantFollowUpInfo(String survey);
}
