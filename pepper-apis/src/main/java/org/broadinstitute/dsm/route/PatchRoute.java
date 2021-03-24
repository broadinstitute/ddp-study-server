package org.broadinstitute.dsm.route;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.*;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.exception.DuplicateException;
import org.broadinstitute.dsm.model.AbstractionWrapper;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.Patch;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.MedicalRecordUtil;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.broadinstitute.dsm.util.PatchUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PatchRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(PatchRoute.class);

    private NotificationUtil notificationUtil;
    private PatchUtil patchUtil;

    public PatchRoute(@NonNull NotificationUtil notificationUtil, @NonNull PatchUtil patchUtil) {
        this.notificationUtil = notificationUtil;
        this.patchUtil = patchUtil;
    }

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        if (patchUtil.getColumnNameMap() == null) {
            return new RuntimeException("ColumnNameMap is null!");
        }
        if (UserUtil.checkUserAccess(null, userId, "mr_view") || UserUtil.checkUserAccess(null, userId, "mr_abstracter")
                || UserUtil.checkUserAccess(null, userId, "mr_qc") || UserUtil.checkUserAccess(null, userId, "pt_list_view")) {
            try {
                String requestBody = request.body();
                Patch patch = new Gson().fromJson(requestBody, Patch.class);
                if (StringUtils.isNotBlank(patch.getId())) {
                    //multiple values are changing
                    if (patch.getNameValues() != null && !patch.getNameValues().isEmpty()) {
                        List<NameValue> nameValues = new ArrayList<>();
                        for (NameValue nameValue : patch.getNameValues()) {
                            DBElement dbElement = patchUtil.getColumnNameMap().get(nameValue.getName());
                            if (dbElement != null) {
                                if (!Patch.patch(patch.getId(), patch.getUser(), nameValue, dbElement)) {
                                    return new RuntimeException("An error occurred while attempting to patch ");
                                }
                                if (nameValue.getName().indexOf("question") > -1) {
                                    User user = User.getUser(patch.getUser());
                                    JSONObject jsonObject = new JSONObject(nameValue.getValue().toString());
                                    JSONArray questionArray = new JSONArray(jsonObject.get("questions").toString());
                                    boolean writeBack = false;
                                    for (int i = 0; i < questionArray.length(); i++) {
                                        JSONObject question = questionArray.getJSONObject(i);
                                        if (question.optString("status") != null && question.optString("status").equals("sent")) {
                                            if (question.optString("email") != null && question.optString("question") != null) {
                                                notificationUtil.sentAbstractionExpertQuestion(user.getEmail(), user.getName(), question.optString("email"),
                                                        patch.getFieldName(), question.optString("question"), notificationUtil.getTemplate("DSM_ABSTRACTION_EXPERT_QUESTION"));
                                            }
                                            question.put("status", "done");
                                            writeBack = true;
                                        }
                                    }
                                    if (writeBack) {
                                        jsonObject.put("questions", questionArray);
                                        String str = jsonObject.toString();
                                        nameValue.setValue(str);
                                        if (!Patch.patch(patch.getId(), patch.getUser(), nameValue, dbElement)) {
                                            return new RuntimeException("An error occurred while attempting to patch ");
                                        }
                                        nameValues.add(nameValue);
                                    }
                                }
                            }
                            else {
                                throw new RuntimeException("DBElement not found in ColumnNameMap: " + nameValue.getName());
                            }
                        }
                        return new Result(200, new GsonBuilder().serializeNulls().create().toJson(nameValues));
                    }
                    else {
                        // mr changes
                        DBElement dbElement = patchUtil.getColumnNameMap().get(patch.getNameValue().getName());
                        if (dbElement != null) {
                            if (Patch.patch(patch.getId(), patch.getUser(), patch.getNameValue(), dbElement)) {
                                List<NameValue> nameValues = setWorkflowRelatedFields(patch);
                                //return nameValues with nulls
                                return new Result(200, new GsonBuilder().serializeNulls().create().toJson(nameValues));
                            }
                        }
                        else {
                            throw new RuntimeException("DBElement not found in ColumnNameMap: " + patch.getNameValue().getName());
                        }
                    }
                }
                else if (StringUtils.isNotBlank(patch.getParent()) && StringUtils.isNotBlank(patch.getParentId())) {
                    if (Patch.PARTICIPANT_ID.equals(patch.getParent())) {
                        if (StringUtils.isNotBlank(patch.getFieldId())) {
                            //abstraction related change
                            //multiple value
                            if (patch.getNameValues() != null && !patch.getNameValues().isEmpty()) {
                                String primaryKeyId = null;
                                for (NameValue nameValue : patch.getNameValues()) {
                                    DBElement dbElement = patchUtil.getColumnNameMap().get(nameValue.getName());
                                    if (dbElement != null) {
                                        if (primaryKeyId == null) {
                                            primaryKeyId = AbstractionWrapper.createNewAbstractionFieldValue(patch.getParentId(), patch.getFieldId(), patch.getUser(), nameValue, dbElement);
                                        }
                                        if (!Patch.patch(primaryKeyId, patch.getUser(), nameValue, dbElement)) {
                                            return new RuntimeException("An error occurred while attempting to patch ");
                                        }
                                    }
                                    else {
                                        throw new RuntimeException("DBElement not found in ColumnNameMap: " + nameValue.getName());
                                    }
                                }
                                Map<String, String> map = new HashMap<>();
                                map.put("primaryKeyId", primaryKeyId);
                                //return map with nulls
                                return new Result(200, new GsonBuilder().serializeNulls().create().toJson(map));
                            }
                            else {
                                //single value
                                DBElement dbElement = patchUtil.getColumnNameMap().get(patch.getNameValue().getName());
                                if (dbElement != null) {
                                    String primaryKeyId = AbstractionWrapper.createNewAbstractionFieldValue(patch.getParentId(), patch.getFieldId(), patch.getUser(), patch.getNameValue(), dbElement);
                                    Map<String, String> map = new HashMap<>();
                                    map.put("primaryKeyId", primaryKeyId);
                                    //return map with nulls
                                    return new Result(200, new GsonBuilder().serializeNulls().create().toJson(map));
                                }
                                else {
                                    throw new RuntimeException("DBElement not found in ColumnNameMap: " + patch.getNameValue().getName());
                                }
                            }
                        }
                        else {
                            //medical record tracking related change
                            Number mrID = MedicalRecordUtil.isInstitutionTypeInDB(patch.getParentId());
                            if (mrID == null) {
                                // mr of that type doesn't exist yet, so create an institution and mr
                                MedicalRecordUtil.writeInstitutionIntoDb(patch.getParentId(), MedicalRecordUtil.NOT_SPECIFIED);
                                mrID = MedicalRecordUtil.isInstitutionTypeInDB(patch.getParentId());
                            }
                            if (mrID != null) {
                                // a mr of that type already exits, add oncHistoryDetails to it
                                String oncHistoryDetailId = OncHistoryDetail.createNewOncHistoryDetail(mrID.toString(), patch.getUser());
                                List<NameValue> nameValues = null;
                                Map<String, String> map = new HashMap<>();
                                //facility was added
                                if (patch.getNameValues() != null && !patch.getNameValues().isEmpty()) {
                                    for (NameValue nameValue : patch.getNameValues()) {
                                        DBElement dbElement = patchUtil.getColumnNameMap().get(nameValue.getName());
                                        if (dbElement != null) {
                                            if (!Patch.patch(oncHistoryDetailId, patch.getUser(), nameValue, dbElement)) {
                                                return new RuntimeException("An error occurred while attempting to patch ");
                                            }
                                        }
                                        else {
                                            throw new RuntimeException("DBElement not found in ColumnNameMap: " + nameValue.getName());
                                        }
                                    }
                                }
                                else {
                                    DBElement dbElement = patchUtil.getColumnNameMap().get(patch.getNameValue().getName());
                                    if (dbElement != null) {
                                        if (Patch.patch(oncHistoryDetailId, patch.getUser(), patch.getNameValue(), dbElement)) {
                                            nameValues = setWorkflowRelatedFields(patch);
                                            //set oncHistoryDetails created if it is a oncHistoryDetails value without a ID, otherwise created should already be set
                                            if (dbElement.getTableName().equals(DBConstants.DDP_ONC_HISTORY_DETAIL)) {
                                                NameValue oncHistoryCreated = OncHistory.setOncHistoryCreated(patch.getParentId(), patch.getUser());
                                                if (oncHistoryCreated != null && oncHistoryCreated.getValue() != null) {
                                                    nameValues.add(oncHistoryCreated);
                                                }
                                            }
                                        }
                                    }
                                    else {
                                        throw new RuntimeException("DBElement not found in ColumnNameMap: " + patch.getNameValue().getName());
                                    }
                                }

                                // add oncHistoryId and NameValues of objects changed by workflow to json and sent it back to UI
                                map.put("oncHistoryDetailId", oncHistoryDetailId);
                                nameValues.add(new NameValue("request", OncHistoryDetail.STATUS_REVIEW));
                                map.put("NameValue", new GsonBuilder().serializeNulls().create().toJson(nameValues));
                                //return map with nulls
                                return new Result(200, new GsonBuilder().serializeNulls().create().toJson(map));
                            }
                            else {
                                logger.error("No medical record id for oncHistoryDetails ");
                            }
                        }
                    }
                    else if (Patch.ONC_HISTORY_ID.equals(patch.getParent())) {
                        String tissueId = Tissue.createNewTissue(patch.getParentId(), patch.getUser());
                        DBElement dbElement = patchUtil.getColumnNameMap().get(patch.getNameValue().getName());
                        if (dbElement != null) {
                            if (Patch.patch(tissueId, patch.getUser(), patch.getNameValue(), dbElement)) {
                                List<NameValue> nameValues = setWorkflowRelatedFields(patch);
                                Map<String, String> map = new HashMap<>();
                                map.put("tissueId", tissueId);
                                if (!nameValues.isEmpty()) {
                                    map.put("NameValue", new GsonBuilder().serializeNulls().create().toJson(nameValues));
                                    return new Result(200, new GsonBuilder().serializeNulls().create().toJson(map));
                                }
                                else {
                                    return new Result(200, new GsonBuilder().serializeNulls().create().toJson(map));
                                }
                            }
                        }
                        else {
                            throw new RuntimeException("DBElement not found in ColumnNameMap: " + patch.getNameValue().getName());
                        }
                    }
                }
                throw new RuntimeException("Id and parentId was null");
            }
            catch (DuplicateException e) {
                return new Result(500, "Duplicate value");
            }
            catch (Exception e) {
                throw new RuntimeException("An error occurred while attempting to patch ", e);
            }
        }
        else {
            response.status(500);
            return new Result(500, UserErrorMessages.NO_RIGHTS);
        }
    }

    private List<NameValue> setWorkflowRelatedFields(@NonNull Patch patch) {
        List<NameValue> nameValues = new ArrayList<>();
        //mr request workflow
        if (patch.getNameValue().getName().equals("m.faxSent")) {
            nameValues.add(setAdditionalValue("m.faxSentBy", patch, patch.getUser()));
            nameValues.add(setAdditionalValue("m.faxConfirmed", patch, patch.getNameValue().getValue()));
        }
        else if (patch.getNameValue().getName().equals("m.faxSent2")) {
            nameValues.add(setAdditionalValue("m.faxSent2By", patch, patch.getUser()));
            nameValues.add(setAdditionalValue("m.faxConfirmed2", patch, patch.getNameValue().getValue()));
        }
        else if (patch.getNameValue().getName().equals("m.faxSent3")) {
            nameValues.add(setAdditionalValue("m.faxSent3By", patch, patch.getUser()));
            nameValues.add(setAdditionalValue("m.faxConfirmed3", patch, patch.getNameValue().getValue()));
        }
        //tissue request workflow
        else if (patch.getNameValue().getName().equals("oD.tFaxSent")) {
            nameValues.add(setAdditionalValue("oD.tFaxSentBy", patch, patch.getUser()));
            nameValues.add(setAdditionalValue("oD.tFaxConfirmed", patch, patch.getNameValue().getValue()));
            nameValues.add(setAdditionalValue("oD.request", patch, "sent"));
        }
        else if (patch.getNameValue().getName().equals("oD.tFaxSent2")) {
            nameValues.add(setAdditionalValue("oD.tFaxSent2By", patch, patch.getUser()));
            nameValues.add(setAdditionalValue("oD.tFaxConfirmed2", patch, patch.getNameValue().getValue()));
            nameValues.add(setAdditionalValue("oD.request", patch, "sent"));
        }
        else if (patch.getNameValue().getName().equals("oD.tFaxSent3")) {
            nameValues.add(setAdditionalValue("oD.tFaxSent3By", patch, patch.getUser()));
            nameValues.add(setAdditionalValue("oD.tFaxConfirmed3", patch, patch.getNameValue().getValue()));
            nameValues.add(setAdditionalValue("oD.request", patch, "sent"));
        }
        else if (patch.getNameValue().getName().equals("oD.tissueReceived")) {
            nameValues.add(setAdditionalValue("oD.request", patch, "received"));
        }
        else if (patch.getNameValue().getName().equals("t.tissueReturnDate")) {
            if (StringUtils.isNotBlank(patch.getNameValue().getValue().toString())) {
                nameValues.add(setAdditionalValue("oD.request", new Patch(patch.getParentId(), "participantId",
                        null, patch.getUser(), patch.getNameValue(), patch.getNameValues()), "returned"));
            }
            else {
                Boolean hasReceivedDate = OncHistoryDetail.hasReceivedDate(patch);

                if (hasReceivedDate) {
                    nameValues.add(setAdditionalValue("oD.request", new Patch(patch.getParentId(), "participantId",
                            null, patch.getUser(), patch.getNameValue(), patch.getNameValues()), "received"));
                }
                else {
                    nameValues.add(setAdditionalValue("oD.request", new Patch(patch.getParentId(), "participantId",
                            null, patch.getUser(), patch.getNameValue(), patch.getNameValues()), "sent"));
                }
            }
        }
        else if (patch.getNameValue().getName().equals("oD.unableToObtain") && (boolean) patch.getNameValue().getValue()) {
        }
        else if (patch.getNameValue().getName().equals("oD.unableToObtain") && !(boolean) patch.getNameValue().getValue()) {
            Boolean hasReceivedDate = OncHistoryDetail.hasReceivedDate(patch);

            if (hasReceivedDate) {
                nameValues.add(setAdditionalValue("oD.request", new Patch(patch.getId(), "participantId",
                        patch.getParentId(), patch.getUser(), patch.getNameValue(), patch.getNameValues()), "received"));
            }
            else {
                nameValues.add(setAdditionalValue("oD.request", new Patch(patch.getId(), "participantId",
                        patch.getParentId(), patch.getUser(), patch.getNameValue(), patch.getNameValues()), "sent"));
            }
        }
        return nameValues;
    }

    private NameValue setAdditionalValue(String additionalValue, @NonNull Patch patch, @NonNull Object value) {
        DBElement dbElement = patchUtil.getColumnNameMap().get(additionalValue);
        if (dbElement != null) {
            NameValue nameValue = new NameValue(additionalValue, value);
            Patch.patch(patch.getId(), patch.getUser(), nameValue, dbElement);
            return nameValue;
        }
        else {
            throw new RuntimeException("DBElement not found in ColumnNameMap: " + additionalValue);
        }
    }
}
