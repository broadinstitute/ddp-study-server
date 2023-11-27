package org.broadinstitute.dsm.route;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.AbstractionActivity;
import org.broadinstitute.dsm.db.AbstractionField;
import org.broadinstitute.dsm.db.AbstractionFieldValue;
import org.broadinstitute.dsm.db.AbstractionFinal;
import org.broadinstitute.dsm.db.AbstractionGroup;
import org.broadinstitute.dsm.db.structure.DBElement;
import org.broadinstitute.dsm.model.AbstractionQCWrapper;
import org.broadinstitute.dsm.model.AbstractionWrapper;
import org.broadinstitute.dsm.model.NameValue;
import org.broadinstitute.dsm.model.Value;
import org.broadinstitute.dsm.model.patch.Patch;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.AbstractionUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.Request;
import spark.Response;

public class AbstractionRoute extends RequestHandler {

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        String requestBody = request.body();
        if (StringUtils.isNotBlank(requestBody)) {
            JsonObject jsonObject = new JsonParser().parse(requestBody).getAsJsonObject();
            String ddpParticipantId = jsonObject.get(RequestParameter.DDP_PARTICIPANT_ID).getAsString();
            String userIdReq = UserUtil.getUserId(request);
            String realm = jsonObject.get(RequestParameter.DDP_REALM).getAsString();

            if (UserUtil.checkUserAccess(realm, userId, "mr_abstracter", userIdReq)
                    || UserUtil.checkUserAccess(realm, userId, "mr_qc", userIdReq)) {
                if (StringUtils.isNotBlank(ddpParticipantId)) {
                    String status = null;
                    if (jsonObject.has(RequestParameter.STATUS)) {
                        status = jsonObject.get(RequestParameter.STATUS).getAsString();
                    }
                    Integer userIdRequest = null;
                    if (jsonObject.has(UserUtil.USER_ID)) {
                        userIdRequest = Integer.parseInt(jsonObject.get(UserUtil.USER_ID).getAsString());
                    }
                    AbstractionActivity abstractionActivity = null;
                    if (jsonObject.has("abstraction")) {
                        Gson gson = new GsonBuilder().create();
                        abstractionActivity = gson.fromJson(jsonObject.get("abstraction").getAsJsonObject(), AbstractionActivity.class);
                    }
                    if (abstractionActivity != null && userIdRequest != null) {
                        // updated filesUsed
                        if (status == null) {
                            return new Result(200, new GsonBuilder().serializeNulls().create()
                                    .toJson(AbstractionActivity.changeAbstractionActivity(abstractionActivity, userIdRequest,
                                            abstractionActivity.getAStatus())));

                        } else {
                            //changing activity of abstraction
                            //submit abstraction
                            if (AbstractionUtil.STATUS_SUBMIT.equals(status)) {
                                boolean submit = true;
                                List<AbstractionGroup> fieldValues = null;
                                if (AbstractionUtil.ACTIVITY_ABSTRACTION.equals(abstractionActivity.getActivity())) {
                                    fieldValues = AbstractionUtil.getActivityFieldValues(realm, ddpParticipantId,
                                            AbstractionUtil.ACTIVITY_ABSTRACTION);
                                } else if (AbstractionUtil.ACTIVITY_REVIEW.equals(abstractionActivity.getActivity())) {
                                    fieldValues = AbstractionUtil.getActivityFieldValues(realm, ddpParticipantId,
                                            AbstractionUtil.ACTIVITY_REVIEW);
                                } else if (AbstractionUtil.ACTIVITY_QC.equals(abstractionActivity.getActivity())) {
                                    fieldValues = AbstractionUtil.getQCFieldValue(realm, ddpParticipantId);
                                } else {
                                    throw new RuntimeException("Error missing ddpParticipantId");
                                }
                                if (fieldValues != null) {
                                    for (AbstractionGroup group : fieldValues) {
                                        for (AbstractionField field : group.getFields()) {
                                            AbstractionFieldValue fieldValue = field.getFieldValue();
                                            //if qc check if all fields which first and second abstracter disagree are answered by qc
                                            if (AbstractionUtil.ACTIVITY_QC.equals(abstractionActivity.getActivity())) {
                                                AbstractionQCWrapper wrapper = field.getQcWrapper();
                                                if (!wrapper.getEquals() && StringUtils.isBlank(fieldValue.getValue())
                                                        && !fieldValue.isNoData()) {
                                                    submit = false;
                                                    break;
                                                }
                                            } else {
                                                //if first and second abstracter check if all fields have a value or are set to noData
                                                if (!fieldValue.isNoData() && StringUtils.isBlank(fieldValue.getValue())) {
                                                    submit = false;
                                                    break;
                                                } else if (StringUtils.isNotBlank(fieldValue.getValue())
                                                        && fieldValue.getValue().indexOf(AbstractionUtil.DATE_STRING) > -1) {
                                                    String jsonValue = fieldValue.getValue();
                                                    if (jsonValue.startsWith("[")) {
                                                        JsonArray array = new JsonParser().parse(jsonValue).getAsJsonArray();
                                                        for (int i = 0; i < array.size(); i++) {
                                                            JsonObject j = array.get(i).getAsJsonObject();
                                                            Set<Map.Entry<String, JsonElement>> entries = j.entrySet();
                                                            for (Map.Entry<String, JsonElement> entry : entries) {
                                                                if (entry.getValue() == null) {
                                                                    submit = false;
                                                                    break;
                                                                }
                                                                if (!entry.getValue().isJsonObject() && !entry.getValue().isJsonNull()) {
                                                                    String test = entry.getValue().getAsString();
                                                                    if (StringUtils.isNotBlank(test)
                                                                            && test.indexOf(AbstractionUtil.DATE_STRING) > -1) {
                                                                        if (!AbstractionUtil.isDateStringSet(test)) {
                                                                            submit = false;
                                                                            break;
                                                                        }
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
                                                                    dateKey = values.stream().filter(e -> e.getType().equals("date"))
                                                                            .findFirst().get();
                                                                }
                                                                orderJson = AbstractionUtil.orderArray(fieldValue.getValue(),
                                                                        dateKey.getValue());
                                                            }
                                                            //writing ordered json into db
                                                            if (StringUtils.isNotBlank(orderJson)
                                                                    && !orderJson.equals(fieldValue.getValue())) {
                                                                if (AbstractionUtil.ACTIVITY_ABSTRACTION.equals(
                                                                        abstractionActivity.getActivity())) {
                                                                    Patch.patch(String.valueOf(fieldValue.getPrimaryKeyId()), "SYSTEM",
                                                                            new NameValue(DBConstants.VALUE, orderJson),
                                                                            new DBElement(DBConstants.MEDICAL_RECORD_ABSTRACTION, "",
                                                                                    DBConstants.MEDICAL_RECORD_ABSTRACTION_ID,
                                                                                    DBConstants.VALUE));
                                                                } else if (AbstractionUtil.ACTIVITY_REVIEW.equals(
                                                                        abstractionActivity.getActivity())) {
                                                                    Patch.patch(String.valueOf(fieldValue.getPrimaryKeyId()), "SYSTEM",
                                                                            new NameValue(DBConstants.VALUE, orderJson),
                                                                            new DBElement(DBConstants.MEDICAL_RECORD_REVIEW, "",
                                                                                    DBConstants.MEDICAL_RECORD_REVIEW_ID,
                                                                                    DBConstants.VALUE));
                                                                } else if (AbstractionUtil.ACTIVITY_QC.equals(
                                                                        abstractionActivity.getActivity())) {
                                                                    Patch.patch(String.valueOf(fieldValue.getPrimaryKeyId()), "SYSTEM",
                                                                            new NameValue(DBConstants.VALUE, orderJson),
                                                                            new DBElement(DBConstants.MEDICAL_RECORD_QC, "",
                                                                                    DBConstants.MEDICAL_RECORD_QC_ID, DBConstants.VALUE));
                                                                }
                                                            }
                                                        }
                                                    } else {
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
                                        abstractionActivity =
                                                AbstractionActivity.changeAbstractionActivity(abstractionActivity, userIdRequest,
                                                        AbstractionUtil.STATUS_DONE);
                                        //if qc is finished save final data in table for data release
                                        if (AbstractionUtil.ACTIVITY_QC.equals(abstractionActivity.getActivity())) {
                                            for (AbstractionGroup group : fieldValues) {
                                                for (AbstractionField field : group.getFields()) {
                                                    AbstractionFieldValue fieldValue = field.getFieldValue();
                                                    AbstractionQCWrapper wrapper = field.getQcWrapper();
                                                    if (StringUtils.isNotBlank(fieldValue.getValue())) {
                                                        //save value entered by qc
                                                        AbstractionFinal.insertFinalAbstractionValue(fieldValue, realm);
                                                    } else {
                                                        //if nothing was entered by qc use abstraction value
                                                        // (in that case abstraction and review are same!)
                                                        // if (StringUtils.isNotBlank(wrapper.getAbstraction().getValue())) {
                                                        AbstractionFinal.insertFinalAbstractionValue(wrapper.getAbstraction(),
                                                                fieldValue.getMedicalRecordAbstractionFieldId(),
                                                                fieldValue.getParticipantId(), realm);
                                                        //                                                    }
                                                    }
                                                }
                                            }
                                            AbstractionActivity.startAbstractionActivity(ddpParticipantId, realm, userIdRequest,
                                                    AbstractionUtil.ACTIVITY_FINAL, AbstractionUtil.STATUS_DONE);
                                        }
                                        return new Result(200, new GsonBuilder().serializeNulls().create().toJson(abstractionActivity));
                                    } else {
                                        //if abstraction is not done!
                                        if (AbstractionUtil.ACTIVITY_QC.equals(abstractionActivity.getActivity())) {
                                            return new Result(500, "QC not complete");
                                        }
                                        return new Result(500, "Abstraction not complete");
                                    }
                                }
                            } else if (AbstractionUtil.STATUS_CLEAR.equals(status)) {
                                //break lock
                                return new Result(200, new GsonBuilder().serializeNulls().create()
                                        .toJson(AbstractionActivity.changeAbstractionActivity(abstractionActivity, userIdRequest,
                                                AbstractionUtil.STATUS_CLEAR)));
                            } else {
                                //set abstraction to 'in_progress'
                                if (AbstractionUtil.STATUS_NOT_STARTED.equals(abstractionActivity.getAStatus())) {
                                    return new Result(200, new GsonBuilder().serializeNulls().create()
                                            .toJson(AbstractionActivity.startAbstractionActivity(ddpParticipantId, realm, userIdRequest,
                                                    abstractionActivity.getActivity(), status)));
                                } else {
                                    return new Result(200, new GsonBuilder().serializeNulls().create()
                                            .toJson(AbstractionActivity.changeAbstractionActivity(abstractionActivity, userIdRequest,
                                                    AbstractionUtil.STATUS_IN_PROGRESS)));
                                }
                            }
                        }
                    } else {
                        //getting field values, if abstraction is not done -
                        // if it is done values will be in abstractionSummary in the ParticipantWrapper
                        return AbstractionWrapper.getAbstractionFieldValue(realm, ddpParticipantId);
                    }
                }
            } else {
                response.status(500);
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        }
        throw new RuntimeException("Error missing ddpParticipantId");
    }

}
