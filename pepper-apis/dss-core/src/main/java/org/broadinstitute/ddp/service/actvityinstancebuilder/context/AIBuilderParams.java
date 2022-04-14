package org.broadinstitute.ddp.service.actvityinstancebuilder.context;

import org.broadinstitute.ddp.content.ContentStyle;
import org.broadinstitute.ddp.db.dto.UserActivityInstanceSummary;
import org.broadinstitute.ddp.service.actvityinstancebuilder.ActivityInstanceFromDefinitionBuilder;


/**
 * Parameters for {@link ActivityInstanceFromDefinitionBuilder}.
 */
public class AIBuilderParams {

    private final String studyGuid;
    private final String instanceGuid;

    private final String userGuid;

    private String operatorGuid;

    private ContentStyle style;
    private String isoLangCode;

    private UserActivityInstanceSummary instanceSummary;

    private boolean readPreviousInstanceId;
    private boolean disableTemplatesRendering;

    public static AIBuilderParams createParams(String userGuid, String studyGuid, String instanceGuid) {
        return new AIBuilderParams(userGuid, studyGuid, instanceGuid);
    }

    private AIBuilderParams(String userGuid, String studyGuid, String instanceGuid) {
        this.userGuid = userGuid;
        this.studyGuid = studyGuid;
        this.instanceGuid = instanceGuid;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public String getInstanceGuid() {
        return instanceGuid;
    }

    public String getUserGuid() {
        return userGuid;
    }

    public String getOperatorGuid() {
        return operatorGuid;
    }

    public AIBuilderParams setOperatorGuid(String operatorGuid) {
        this.operatorGuid = operatorGuid;
        return this;
    }

    public ContentStyle getStyle() {
        return style;
    }

    public AIBuilderParams setStyle(ContentStyle style) {
        this.style = style;
        return this;
    }

    public String getIsoLangCode() {
        return isoLangCode;
    }

    public AIBuilderParams setIsoLangCode(String isoLangCode) {
        this.isoLangCode = isoLangCode;
        return this;
    }

    public UserActivityInstanceSummary getInstanceSummary() {
        return instanceSummary;
    }

    public AIBuilderParams setInstanceSummary(UserActivityInstanceSummary instanceSummary) {
        this.instanceSummary = instanceSummary;
        return this;
    }

    public boolean isDisableTemplatesRendering() {
        return disableTemplatesRendering;
    }

    public AIBuilderParams setDisableTemplatesRendering(boolean disableTemplatesRendering) {
        this.disableTemplatesRendering = disableTemplatesRendering;
        return this;
    }

    public boolean isReadPreviousInstanceId() {
        return readPreviousInstanceId;
    }

    public AIBuilderParams setReadPreviousInstanceId(boolean readPreviousInstanceId) {
        this.readPreviousInstanceId = readPreviousInstanceId;
        return this;
    }
}
