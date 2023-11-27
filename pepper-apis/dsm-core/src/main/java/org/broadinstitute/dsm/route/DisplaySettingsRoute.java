package org.broadinstitute.dsm.route;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.Assignee;
import org.broadinstitute.dsm.db.Cancer;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.Drug;
import org.broadinstitute.dsm.db.FieldSettings;
import org.broadinstitute.dsm.db.InstanceSettings;
import org.broadinstitute.dsm.db.KitType;
import org.broadinstitute.dsm.db.ViewFilter;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.settings.InstanceSettingsDto;
import org.broadinstitute.dsm.model.KitRequestSettings;
import org.broadinstitute.dsm.model.KitSubKits;
import org.broadinstitute.dsm.model.ddp.PreferredLanguage;
import org.broadinstitute.dsm.model.elastic.retrieve.activities.ActivityDefinitionsRetrieverFactory;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.AbstractionUtil;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.broadinstitute.dsm.util.PatchUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.handlers.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class DisplaySettingsRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(DisplaySettingsRoute.class);

    private PatchUtil patchUtil;

    public DisplaySettingsRoute(@NonNull PatchUtil patchUtil) {
        this.patchUtil = patchUtil;
    }

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        if (patchUtil.getColumnNameMap() == null) {
            throw new RuntimeException("ColumnNameMap is null!");
        }
        QueryParamsMap queryParams = request.queryMap();
        String realm = request.params(RequestParameter.REALM);
        if (StringUtils.isBlank(realm)) {
            logger.error("Realm is empty");
        }
        String ddpGroupId = DDPInstance.getDDPGroupId(realm);
        if (StringUtils.isBlank(ddpGroupId)) {
            logger.error("GroupId is empty");
        }
        String userIdRequest = UserUtil.getUserId(request);
        if (!userId.equals(userIdRequest)) {
            throw new RuntimeException("User id was not equal. User Id in token " + userId + " user Id in request " + userIdRequest);
        }
        if (UserUtil.checkUserAccess(realm, userId, "mr_view", userIdRequest)
                || UserUtil.checkUserAccess(realm, userId, "pt_list_view", userIdRequest)) {
            String parent = queryParams.get("parent").value();
            if (StringUtils.isBlank(parent)) {
                logger.error("Parent is empty");
            }
            DDPInstance instance = DDPInstance.getDDPInstanceWithRole(realm, DBConstants.MEDICAL_RECORD_ACTIVATED);
            if (instance == null) {
                logger.error("Instance was not found");
            }
            if (StringUtils.isNotBlank(realm) && instance != null && StringUtils.isNotBlank(userIdRequest)
                    && StringUtils.isNotBlank(parent) && StringUtils.isNotBlank(ddpGroupId)) {
                Map<String, Object> displaySettings = new HashMap<>();
                InstanceSettings instanceSettings = new InstanceSettings();
                displaySettings.put("assignees", Assignee.getAssignees(realm));
                displaySettings.put("fieldSettings", FieldSettings.getFieldSettings(realm));
                displaySettings.put("drugs", Drug.getDrugList());
                displaySettings.put("cancers", Cancer.getCancers());
                displaySettings.put("activityDefinitions", new ActivityDefinitionsRetrieverFactory(instance).spawn().retrieve());
                displaySettings.put("filters", ViewFilter.getAllFilters(userIdRequest, patchUtil.getColumnNameMap(), parent, ddpGroupId,
                        instance.getDdpInstanceId()));
                displaySettings.put("abstractionFields", AbstractionUtil.getFormControls(realm));
                InstanceSettingsDto instanceSettingsDto = instanceSettings.getInstanceSettings(realm);
                displaySettings.putAll(instanceSettings.getInstanceSettingsAsMap(instanceSettingsDto));
                if (!instance.isHasRole()) {
                    displaySettings.put("hideMRTissueWorkflow", true);
                }
                if (StringUtils.isNotBlank(instance.getUsersIndexES())) {
                    displaySettings.put("hasProxyData", true);
                }
                if (StringUtils.isNotBlank(instance.getParticipantIndexES())) {
                    List<PreferredLanguage> preferredLanguages = DDPRequestUtil.getPreferredLanguages(instance);
                    if (preferredLanguages != null) {
                        displaySettings.put("preferredLanguages", preferredLanguages);
                    }
                }
                HashSet<String> roles = new DDPInstanceDao().getInstanceRoles(instance.getName());
                if (roles.contains(DBConstants.KIT_REQUEST_ACTIVATED)) { //only needed if study is shipping samples per DSM
                    Map<Integer, KitRequestSettings> kitRequestSettingsMap =
                            KitRequestSettings.getKitRequestSettings(instance.getDdpInstanceId());
                    if (kitRequestSettingsMap != null) {
                        List<KitType> kits = new ArrayList<>();
                        List<KitType> kitTypes = KitType.getKitTypes(realm, null);
                        if (kitTypes != null && !kitTypes.isEmpty()) {
                            kitTypes.forEach(kitType -> {
                                KitRequestSettings kitRequestSettings = kitRequestSettingsMap.get(kitType.getKitId());
                                //kit has sub kits add them to displaySettings
                                if (kitRequestSettings != null && kitRequestSettings.getHasSubKits() != 0) {
                                    List<KitSubKits> subKits = kitRequestSettings.getSubKits();
                                    if (subKits != null && !subKits.isEmpty()) {
                                        subKits.forEach(subKit -> {
                                            kits.add(new KitType(subKit.getKitTypeId(), subKit.getKitName(), subKit.getKitName(),
                                                    kitType.isManualSentTrack(), kitType.isExternalShipper(), kitType.getUploadReasons()));
                                        });
                                    }
                                } else {
                                    //kit doesn't have subkits add kitType
                                    kits.add(kitType);
                                }
                            });
                        }
                        if (kits != null && !kits.isEmpty()) {
                            displaySettings.put("kitTypes", kits);
                        }
                    }
                }
                if (roles.contains(DBConstants.ADD_FAMILY_MEMBER)) {
                    displaySettings.put("addFamilyMember", true);
                }
                if (roles.contains(DBConstants.SHOW_GROUP_FIELDS)) {
                    displaySettings.put("showGroupFields", true);
                }
                if (roles.contains(DBConstants.HAS_CLINICAL_KIT)) {
                    displaySettings.put("hasSequencingOrders", true);
                }

                return displaySettings;
            }
        } else {
            logger.warn(UserErrorMessages.NO_RIGHTS);
            response.status(500);
            return new Result(500, UserErrorMessages.NO_RIGHTS);
        }
        logger.warn(UserErrorMessages.CONTACT_DEVELOPER);
        return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
    }
}
