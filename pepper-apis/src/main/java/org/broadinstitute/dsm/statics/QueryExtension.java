package org.broadinstitute.dsm.statics;

public class QueryExtension {

    public static final String BY_INSTANCE_NAME = " and instance_name = ? ";
    public static final String BY_INSTANCE_ID = " and ddp_instance_id = ? ";
    public static final String BY_STUDY_GUID = " and study_guid = ? ";

    public static final String BY_ROLE_NAME = " and role.name = \'%1\' ";
    public static final String BY_ROLE_NAMES = " and (role.name = \'%1\' or role.name = \'%2\')";
    public static final String BY_ROLE_NAME_START_WITH = " and role.name regexp \'^%1\' ";

    public static final String BY_DDP_PARTICIPANT_ID = " AND p.ddp_participant_id = ?";
    public static final String BY_MEDICAL_RECORD_ID = " AND m.medical_record_id = ?";

    public static final String BY_INSTITUTION_ID = " AND m.institution_id = ?";

    public static final String BY_REALM = " and request.instance_name = ?";

    public static final String BY_REALM_AND_TYPE = " and request.instance_name = ? and request.kit_type_name = ?";

    public static final String KIT_NOT_COMPLETE_NO_ERROR = " and not (kit.kit_complete <=> 1) and not (kit.error <=> 1) and kit.label_url_to is not null and kit.deactivated_date is null";
    public static final String KIT_COMPLETE = " and kit.kit_complete = 1 and kit.deactivated_date is null";
    public static final String KIT_NOT_COMPLETE_HAS_ERROR = " and not (kit.kit_complete <=> 1) and kit.error = 1 and kit.deactivated_date is null";
    public static final String KIT_RECEIVED = " and kit.receive_date is not null and kit.deactivated_date is null";
    public static final String KIT_NO_LABEL = " and kit.easypost_to_id is null and kit.deactivated_date is null and not (kit.error <=> 1) and not (kit.kit_complete <=> 1) and not (kit.needs_approval <=> 1)";
    public static final String KIT_LABEL_NOT_TRIGGERED = " and kit.label_date is null";
    public static final String KIT_LABEL_TRIGGERED = " and kit.easypost_to_id is null and kit.deactivated_date is null and kit.label_date is not null and not (kit.error <=> 1) and not (kit.kit_complete <=> 1)";
    public static final String KIT_DEACTIVATED = " and kit.deactivated_date is not null";
    public static final String KIT_BY_KIT_REQUEST_ID = " and kit.dsm_kit_request_id = ?";
    public static final String KIT_WAITING = " and kit.needs_approval = 1 and authorization is null";

    public static final String ORDER_PARTICIPANT = " order by par.participant_id, med.medical_record_id, oncDetail.onc_history_detail_id";

    public static final String BY_USER_EMAIL = " AND user.email = ?";

    public static final String BY_USER_ID = " AND user.user_id = ?";
    public static final String ORDER_BY_KIT_TYPE_ID = " order by type.kit_type_id";

    public static final String TISSUE_BY_MEDICAL_RECORD = " and medical_record_id = ?";
    public static final String BY_ONC_HISTORY_DETAIL_ID = " and oD.onc_history_detail_id = ?";

    public static final String DISCARD_KIT_BY_DISCARD_ID = " and kit_discard_id = ?";

    public static final String WHERE_INSTANCE_ID = " where ddp_instance_id = ?";
    public static final String WHERE_REALM_INSTANCE_ID = " where realm.ddp_instance_id = ?";
    public static final String AND_REALM_INSTANCE_ID = " and realm.ddp_instance_id = ?";


}
