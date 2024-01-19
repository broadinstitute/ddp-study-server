-- scripts that will:
-- generate a bunch of sql statements and shell script calls to be run to erase data for a study from dsm and dss.
-- it also sets queued events to happen in the infinite future, effectively disabling them

-- set the study to be deleted
set @study_guid = 'singular' collate 'utf8mb4_unicode_ci';

-- set the gcp environment in which to run the user deletes for dss
set @gcp_project = 'broad-ddp-dev';
use pepperdev;

-- reset queued events to the future
update queued_event set post_after = 99999999999999 where event_configuration_id in
                                                          (select c.event_configuration_id
                                                           from umbrella_study s,
                                                                event_configuration c
                                                           where s.guid = @study
                                                             and s.umbrella_study_id = c.umbrella_study_id)
                                                      and post_after > unix_timestamp();

-- delete governed users in dss
select distinct
    concat('gcloud --project=', @gcp_project,  ' pubsub topics publish dss-tasks --attribute ''taskType=USER_DELETE,participantGuid=',
           u.guid,',operatorGuid=andrew'' --message ''{"comment" : "purging singular data"} '';  sleep 10;')
from user u,
     user_study_enrollment e,
     umbrella_study s,
     user_governance g
where
        u.user_id = e.user_id
  and
        e.study_id = s.umbrella_study_id
  and
        g.participant_user_id = u.user_id
  and
        s.guid = @study_guid;

-- delete non governed users in dss
select distinct
    concat('gcloud --project=', @gcp_project,  ' pubsub topics publish dss-tasks --attribute ''taskType=USER_DELETE,participantGuid=',
           u.guid,',operatorGuid=andrew'' --message ''{"comment" : "purging singular data"} ''; sleep 10;')
from user u,
     user_study_enrollment e,
     umbrella_study s
where
        u.user_id = e.user_id
  and
        e.study_id = s.umbrella_study_id
  and
  -- don't include governed users
    not exists (select 1 from user_governance g where g.operator_user_id = u.user_id)
  and
        s.guid = @study_guid;


use dev_dsm_db;

-- delete kits
select
    concat('delete from ddp_kit where dsm_kit_id = ',k.dsm_kit_id, ';')
from
    ddp_kit_request req,
    ddp_instance i,
    ddp_kit k
where
        i.study_guid = @study_guid
  and
        i.ddp_instance_id = req.ddp_instance_id
  and
        k.dsm_kit_request_id = req.dsm_kit_request_id;

-- delete kit requests
select
    concat('delete from ddp_kit_request where dsm_kit_request_id = ',req.dsm_kit_request_id, ';')
from
    ddp_kit_request req,
    ddp_instance i
where
        i.study_guid = @study_guid
  and
        i.ddp_instance_id = req.ddp_instance_id;

-- delete participant_data
select
    concat('delete from ddp_participant_data where participant_data_id = ',d.participant_data_id, ';')
from
    ddp_instance i,
    ddp_participant_data d
where
        i.study_guid = @study_guid
  and
        d.ddp_instance_id = i.ddp_instance_id;

-- delete participant records
select
    concat('delete from ddp_participant_record where participant_id = ',participant.participant_id, ';')

FROM ddp_participant participant, ddp_participant_record record, ddp_instance i

where
        i.study_guid = @study_guid and
        participant.participant_id = record.participant_id and
        participant.ddp_instance_id = i.ddp_instance_id;


-- delete ddp_participant from dsm
select
    concat('delete from ddp_participant where participant_id = ',p.participant_id, ';')
from
    ddp_instance i,
    ddp_participant p
where
        i.study_guid = @study_guid
  and
        p.ddp_instance_id = i.ddp_instance_id;

-- delete kit settings from dsm
select
    concat('delete from ddp_kit_request_settings where ddp_kit_request_settings_id = ',krs.ddp_kit_request_settings_id, ';')
FROM ddp_kit_request_settings krs, ddp_instance i
where
        i.study_guid = @study_guid and krs.ddp_instance_id = i.ddp_instance_id;

-- delete instance roles from dsm
select
    concat('delete from ddp_instance_role where ddp_instance_id = ',i.ddp_instance_id, ';')
FROM ddp_instance_role dir, ddp_instance i
where
        i.study_guid = @study_guid and dir.ddp_instance_id = i.ddp_instance_id;

-- delete instance groups from dsm
select
    concat('delete from ddp_instance_group where instance_group_id = ', dig.instance_group_id, ';')
FROM ddp_instance_group dig,  ddp_instance i
where
        i.study_guid = @study_guid and dig.ddp_instance_id = i.ddp_instance_id;

-- delete group roles from dsm
select
    concat('delete from ddp_group_role where group_id = ', g.group_id, ';')
FROM ddp_group g
where g.name = @study_guid;

-- delete access for the group for the study

select
    concat('delete from access_user_role_group where group_id = (select group_id from ddp_group where name =''',@study_guid,''');');

-- delete ddp group from dsm
select
    concat('delete from ddp_group where group_id = ', g.group_id, ';')
FROM ddp_group g
where g.name = @study_guid;

-- delete participant exit from dsm
select
    concat('delete from ddp_participant_exit where ddp_participant_exit_id = ', e.ddp_participant_exit_id, ';')
FROM ddp_participant_exit e, ddp_instance i
where i.study_guid = @study_guid and e.ddp_instance_id = i.ddp_instance_id;

-- disable the instance just in case deleting the instance row fails
select concat('update ddp_instance set is_active = 0,
                        es_users_index = null,
                        es_activity_definition_index = null,
                        es_users_index = null
where study_guid = ''',@study_guid, ''';');

-- remove all instance settings
select concat('delete from instance_settings where ddp_instance_id = (select ddp_instance_id from ddp_instance
                                                       where study_guid = ''', @study_guid,''';');


select concat('delete from EVENT_QUEUE where DDP_INSTANCE_ID =
                              (select ddp_instance_id from ddp_instance where study_guid = ''', @study_guid,''');');

select concat('delete from cohort_tag where ddp_instance_id =
                             (select ddp_instance_id from ddp_instance where study_guid = ''',@study_guid,''');');
