
/* create new expressions, one for angio, one for brain */
insert into expression (expression_guid,expression_text)
values ('DDP-3899.A','(user.studies["ANGIO"].forms["ANGIOABOUTYOU"].questions["COUNTRY"].answers.hasOption("US") || user.studies["ANGIO"].forms["ANGIOABOUTYOU"].questions["COUNTRY"].answers.hasOption("CA"))')


insert into expression (expression_guid,expression_text)
values ('DDP-3899.B','user.studies["cmi-brain"].forms["ABOUTYOU"].questions["COUNTRY"].answers.hasOption("US") || user.studies["cmi-brain"].forms["ABOUTYOU"].questions["COUNTRY"].answers.hasOption("CA"))')

/* find the activity configurations that need the new preconditions */
select
act.study_activity_code,
evt_cfg.event_configuration_id,
evt_cfg.precondition_expression_id
from
study_activity act,
activity_instance_creation_action a,
umbrella_study angio,
umbrella_study brain,
event_configuration evt_cfg
where
angio.guid = 'ANGIO'
and
brain.guid = 'cmi-brain'
and
a.study_activity_id = act.study_activity_id
and
(
	(act.study_id = angio.umbrella_study_id and act.study_activity_code = 'ANGIOCONSENT')
	or
	(act.study_id = brain.umbrella_study_id and act.study_activity_code = 'CONSENT')
)
and
evt_cfg.event_action_id = a.activity_instance_creation_action_id


/* update angio */
update event_configuration set precondition_expression_id = (select expression_id from expression where expression_guid = 'DDP-3899.A')
where event_configuration_id = [id from above resultset]

/* update brain */
update event_configuration set precondition_expression_id = (select expression_id from expression where expression_guid = 'DDP-3899.B')
where event_configuration_id = [id from above resultset]