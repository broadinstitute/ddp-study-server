package org.broadinstitute.ddp.datstat;


import com.google.gson.JsonElement;
import lombok.NonNull;
import org.broadinstitute.ddp.file.BasicProcessor;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

public class SurveyConfig<T extends SurveyInstance, S extends BasicProcessor> {

    /**
     * Mapping of survey path name (the string in /{survey}/), the survey
     * definition in datstat for said survey, and the class to use
     * as the java model for the survey.
     */

    public enum FollowUpType
    {
        NONE, REPEATING, NONREPEATING
    }

    private String pathName;
    private SurveyDefinition surveyDef;
    private Class<T> surveyClass;
    private Class<S> pdfClass;
    private FollowUpType followUpType;
    private boolean allowsPost;

    private boolean anonymousSurvey;

    public SurveyConfig(String pathName, Class<T> surveyClass, Class<S> pdfClass, SurveyDefinition surveyDef,
                        boolean anonymousSurvey, FollowUpType followUpType, boolean allowsPost) {
        this.pathName = pathName;
        this.surveyClass = surveyClass;
        this.pdfClass = pdfClass;
        this.surveyDef = surveyDef;
        this.anonymousSurvey = anonymousSurvey;
        this.followUpType = followUpType;
        this.allowsPost = allowsPost;
    }

    public Class<T> getSurveyClass() {
        return surveyClass;
    }

    public Class<S> getPdfClass() {
        return pdfClass;
    }

    public SurveyDefinition getSurveyDefinition() {
        return surveyDef;
    }

    public String getSurveyPathName() {return pathName;}

    public boolean isAnonymousSurvey() {
        return anonymousSurvey;
    }

    public FollowUpType getFollowUpType() { return followUpType; }

    public boolean isAllowsPost() { return allowsPost;}

    @Override
    public String toString() {
        return "SurveyConfig{" +
                "pathName='" + pathName + '\'' +
                ", surveyDef=" + surveyDef +
                ", surveyClass=" + surveyClass +
                ", pdfClass=" + pdfClass +
                ", followUpType=" + followUpType.toString() +
                ", allowsPost=" + allowsPost +
                '}';
    }

    /**
     * Builds the mapping between survey url path name, survey definition, and survey instance class
     */
    public static Map<String,SurveyConfig> buildSurveyConfig(@NonNull DatStatUtil datstatUtil, @NonNull Map<String, JsonElement> portalSurveyLookup) throws IOException, ClassNotFoundException {
        Map<String,SurveyConfig> configMap = new HashMap<>();
        Collection<SurveyDefinition> surveyDefs = new SurveyService().fetchSurveys(datstatUtil);

        if (surveyDefs == null) {
            throw new RuntimeException("Unable to find surveys in DatStat.");
        }

        Map<String, SurveyDefinition> map = surveyDefs.stream().collect(Collectors.toMap(SurveyDefinition::getDescription, item -> item));

        for (JsonElement surveyInfo : portalSurveyLookup.values()) {
            SurveyDefinition surveyDef = map.get(surveyInfo.getAsJsonObject().get("surveyDesc").getAsString());

            if (surveyDef != null) {
                String surveyPath = surveyInfo.getAsJsonObject().get("surveyPath").getAsString();;
                String className = surveyInfo.getAsJsonObject().get("className").getAsString();
                String pdfClassName = (surveyInfo.getAsJsonObject().get("pdfClassName") == null) ? null : surveyInfo.getAsJsonObject().get("pdfClassName").getAsString();
                FollowUpType followUpType = (surveyInfo.getAsJsonObject().get("followUpType") == null) ? FollowUpType.NONE : FollowUpType.valueOf(surveyInfo.getAsJsonObject().get("followUpType").getAsString());
                boolean allowsPost = (surveyInfo.getAsJsonObject().get("allowsPost") == null) ? true : surveyInfo.getAsJsonObject().get("allowsPost").getAsBoolean();

                configMap.put(surveyPath, new SurveyConfig(surveyPath, Class.forName(className),
                        (pdfClassName != null) ? Class.forName(pdfClassName) : null, surveyDef,
                        surveyInfo.getAsJsonObject().get("anonymous").getAsBoolean(), followUpType, allowsPost));
            }
        }

        if (configMap.size() == 0) {
            throw new RuntimeException("Unable to map surveys in config to DatStat.");
        }

        return configMap;
    }
}

