select
ddp.instance_name,
part.ddp_participant_id,
inst.type,
inst.ddp_institution_id,
his.created,
his.reviewed,
med.name,
med.contact,
med.phone,
med.fax,
med.fax_sent,
med.fax_confirmed,
med.mr_received,
med.mr_document,
med.mr_problem,
med.mr_problem_text,
med.unable_obtain,
med.duplicate,
med.notes,
(select sum(log.comments is null and log.type = "DATA_REVIEW") as reviewMedicalRecord
    from ddp_medical_record rec2
    left join ddp_medical_record_log log on (rec2.medical_record_id = log.medical_record_id)
    where rec2.institution_id = inst.institution_id) as reviewMedicalRecord
from
ddp_institution inst
left join ddp_participant as part on (part.participant_id = inst.participant_id)
left join ddp_instance as ddp on (ddp.ddp_instance_id = part.ddp_instance_id)
left join ddp_medical_record as med on (med.institution_id = inst.institution_id)
left join access_user assi on (assi.user_id = part.assignee_id_mr)
left join ddp_onc_history his on (his.participant_id = part.participant_id)
where ddp.ddp_instance_id = ?