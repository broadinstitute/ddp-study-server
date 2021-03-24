package org.broadinstitute.dsm.route;

import com.google.gson.*;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.*;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.model.*;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.AbstractionUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import spark.Request;
import spark.Response;

import java.util.List;
import java.util.Set;

public class AbstractionRoute extends RequestHandler {

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        String requestBody = request.body();
        if (StringUtils.isNotBlank(requestBody)) {
            JSONObject jsonObject = new JSONObject(requestBody);
            String ddpParticipantId = (String) jsonObject.get(RequestParameter.DDP_PARTICIPANT_ID);
            String realm = (String) jsonObject.get(RequestParameter.DDP_REALM);

            if (UserUtil.checkUserAccess(realm, userId, "mr_abstracter") || UserUtil.checkUserAccess(realm, userId, "mr_qc")) {
                if (StringUtils.isNotBlank(ddpParticipantId)) {
                    String status = null;
                    if (jsonObject.has(RequestParameter.STATUS) && !jsonObject.isNull(RequestParameter.STATUS)) {
                        status = (String) jsonObject.get(RequestParameter.STATUS);
                    }
                    Integer userIdRequest = null;
                    if (jsonObject.has(UserUtil.USER_ID)) {
                        userIdRequest = Integer.parseInt((String) jsonObject.get(UserUtil.USER_ID));
                    }
                    AbstractionActivity abstractionActivity = null;
                    if (jsonObject.has("abstraction")) {
                        Gson gson = new GsonBuilder().create();
                        abstractionActivity = gson.fromJson(jsonObject.getJSONObject("abstraction").toString(), AbstractionActivity.class);
                    }
                    if (abstractionActivity != null && userIdRequest != null) {
                        // updated filesUsed
                        if (status == null) {
                            return new Result(200, new GsonBuilder().serializeNulls().create().toJson(AbstractionActivity.changeAbstractionActivity(abstractionActivity, userIdRequest, abstractionActivity.getAStatus())));
                        }
                        else {
                            //changing activity of abstraction
                            //submit abstraction
                            if (AbstractionUtil.STATUS_SUBMIT.equals(status)) {
                                boolean submit = true;
                                List<AbstractionGroup> fieldValues = null;
                                if (AbstractionUtil.ACTIVITY_ABSTRACTION.equals(abstractionActivity.getActivity())) {
                                    fieldValues = AbstractionUtil.getActivityFieldValues(realm, ddpParticipantId, AbstractionUtil.ACTIVITY_ABSTRACTION);
                                }
                                else if (AbstractionUtil.ACTIVITY_REVIEW.equals(abstractionActivity.getActivity())) {
                                    fieldValues = AbstractionUtil.getActivityFieldValues(realm, ddpParticipantId, AbstractionUtil.ACTIVITY_REVIEW);
                                }
                                else if (AbstractionUtil.ACTIVITY_QC.equals(abstractionActivity.getActivity())) {
                                    fieldValues = AbstractionUtil.getQCFieldValue(realm, ddpParticipantId);
                                }
                                else {
                                    throw new RuntimeException("Error missing ddpParticipantId");
                                }
                                if (fieldValues != null) {
                                    for (AbstractionGroup group : fieldValues) {
                                        for (AbstractionField field : group.getFields()) {
                                            AbstractionFieldValue fieldValue = field.getFieldValue();
                                            //if qc check if all fields which first and second abstracter disagree are answered by qc
                                            if (AbstractionUtil.ACTIVITY_QC.equals(abstractionActivity.getActivity())) {
                                                AbstractionQCWrapper wrapper = field.getQcWrapper();
                                                if (!wrapper.getEquals() && StringUtils.isBlank(fieldValue.getValue()) && !fieldValue.isNoData()) {
                                                    submit = false;
                                                    break;
                                                }
                                            }
                                            //if first and second abstracter check if all fields have a value or are set to noData
                                            else {
                                                if (!fieldValue.isNoData() && StringUtils.isBlank(fieldValue.getValue())) {
                                                    submit = false;
                                                    break;
                                                }
                                                else if (StringUtils.isNotBlank(fieldValue.getValue()) && fieldValue.getValue().indexOf(AbstractionUtil.DATE_STRING) > -1) {
                                                    String jsonValue = fieldValue.getValue();
                                                    if (jsonValue.startsWith("[")) {
                                                        JSONArray array = new JSONArray(jsonValue);
                                                        for (int i = 0; i < array.length(); i++) {
                                                            JSONObject j = array.getJSONObject(i);
                                                            Set<String> entries = j.keySet();
                                                            for (String entry : entries) {
                                                                String test = j.optString(entry);
                                                                if (StringUtils.isNotBlank(test) && test.indexOf(AbstractionUtil.DATE_STRING) > -1) {
                                                                    if (!AbstractionUtil.isDateStringSet(test)) {
                                                                        submit = false;
                                                                        break;
                                                                    }

                                                                }
                                                            }
                                                            if (!submit) {
                                                                break;
                                                            }
                                                        }
                                                        if (!submit) {
                                                            break;
                                                        }
                                                        //order multi_type_array
                                                        if (submit) {
                                                            String orderJson = null;
                                                            if ("multi_type_array".equals(field.getType())) {
                                                                List<Value> values = field.getPossibleValues();
                                                                Value dateKey = null;
                                                                if (values != null && !values.isEmpty()) {
                                                                    dateKey = values.stream().filter(e -> e.getType().equals("date")).findFirst().get();
                                                                }
                                                                orderJson = AbstractionUtil.orderArray(fieldValue.getValue(), dateKey.getValue());
                                                            }
                                                            //writing ordered json into db
                                                            if (StringUtils.isNotBlank(orderJson) && !orderJson.equals(fieldValue.getValue())) {
                                                                if (AbstractionUtil.ACTIVITY_ABSTRACTION.equals(abstractionActivity.getActivity())) {
                                                                    Patch.patch(String.valueOf(fieldValue.getPrimaryKeyId()), "SYSTEM", new NameValue(DBConstants.VALUE, orderJson),
                                                                            new DBElement(DBConstants.MEDICAL_RECORD_ABSTRACTION, "", DBConstants.MEDICAL_RECORD_ABSTRACTION_ID, DBConstants.VALUE));
                                                                }
                                                                else if (AbstractionUtil.ACTIVITY_REVIEW.equals(abstractionActivity.getActivity())) {
                                                                    Patch.patch(String.valueOf(fieldValue.getPrimaryKeyId()), "SYSTEM", new NameValue(DBConstants.VALUE, orderJson),
                                                                            new DBElement(DBConstants.MEDICAL_RECORD_REVIEW, "", DBConstants.MEDICAL_RECORD_REVIEW_ID, DBConstants.VALUE));
                                                                }
                                                                else if (AbstractionUtil.ACTIVITY_QC.equals(abstractionActivity.getActivity())) {
                                                                    Patch.patch(String.valueOf(fieldValue.getPrimaryKeyId()), "SYSTEM", new NameValue(DBConstants.VALUE, orderJson),
                                                                            new DBElement(DBConstants.MEDICAL_RECORD_QC, "", DBConstants.MEDICAL_RECORD_QC_ID, DBConstants.VALUE));
                                                                }
                                                            }
                                                        }
                                                    }
                                                    else {
                                                        if (!AbstractionUtil.isDateStringSet(jsonValue)) {
                                                            submit = false;
                                                            break;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        if (!submit) {
                                            break;
                                        }
                                    }
                                    //if abstraction is really done, set to 'done'
                                    if (submit) {
                                        abstractionActivity = AbstractionActivity.changeAbstractionActivity(abstractionActivity, userIdRequest, AbstractionUtil.STATUS_DONE);
                                        //if qc is finished save final data in table for data release
                                        if (AbstractionUtil.ACTIVITY_QC.equals(abstractionActivity.getActivity())) {
                                            for (AbstractionGroup group : fieldValues) {
                                                for (AbstractionField field : group.getFields()) {
                                                    AbstractionFieldValue fieldValue = field.getFieldValue();
                                                    AbstractionQCWrapper wrapper = field.getQcWrapper();
                                                    if (StringUtils.isNotBlank(fieldValue.getValue())) {
                                                        //save value entered by qc
                                                        AbstractionFinal.insertFinalAbstractionValue(fieldValue, realm);
                                                    }
                                                    else {
                                                        //if nothing was entered by qc use abstraction value (in that case abstraction and review are same!)
                                                        //                                                    if (StringUtils.isNotBlank(wrapper.getAbstraction().getValue())) {
                                                        AbstractionFinal.insertFinalAbstractionValue(wrapper.getAbstraction(), fieldValue.getMedicalRecordAbstractionFieldId(),
                                                                fieldValue.getParticipantId(), realm);
                                                        //                                                    }
                                                    }
                                                }
                                            }
                                            AbstractionActivity.startAbstractionActivity(ddpParticipantId, realm, userIdRequest, AbstractionUtil.ACTIVITY_FINAL, AbstractionUtil.STATUS_DONE);
                                        }
                                        return new Result(200, new GsonBuilder().serializeNulls().create().toJson(abstractionActivity));
                                    }
                                    //if abstraction is not done!
                                    else {
                                        if (AbstractionUtil.ACTIVITY_QC.equals(abstractionActivity.getActivity())) {
                                            return new Result(500, "QC not complete");
                                        }
                                        return new Result(500, "Abstraction not complete");
                                    }
                                }
                            }
                            //break lock
                            else if (AbstractionUtil.STATUS_CLEAR.equals(status)) {
                                return new Result(200, new GsonBuilder().serializeNulls().create().toJson(AbstractionActivity.changeAbstractionActivity(abstractionActivity, userIdRequest, AbstractionUtil.STATUS_CLEAR)));
                            }
                            //set abstraction to 'in_progress'
                            else {
                                if (AbstractionUtil.STATUS_NOT_STARTED.equals(abstractionActivity.getAStatus())) {
                                    return new Result(200, new GsonBuilder().serializeNulls().create().toJson(AbstractionActivity.startAbstractionActivity(ddpParticipantId, realm, userIdRequest, abstractionActivity.getActivity(), status)));
                                }
                                else {
                                    return new Result(200, new GsonBuilder().serializeNulls().create().toJson(AbstractionActivity.changeAbstractionActivity(abstractionActivity, userIdRequest, AbstractionUtil.STATUS_IN_PROGRESS)));
                                }
                            }
                        }
                    }
                    else {
                        //getting field values, if abstraction is not done - if it is done values will be in abstractionSummary in the ParticipantWrapper
                        return AbstractionWrapper.getAbstractionFieldValue(realm, ddpParticipantId);
                    }
                }
            }
            else {
                response.status(500);
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        }
        throw new RuntimeException("Error missing ddpParticipantId");
    }

}
