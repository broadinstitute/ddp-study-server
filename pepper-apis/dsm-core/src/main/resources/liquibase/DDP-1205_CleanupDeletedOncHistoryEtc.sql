-- this file will be run manually, not by liquibase, but it is here to track
-- our approach to DDP-1205.

-- insert deleted onc history details
insert into deleted_object (original_table, original_primary_key, data, deleted_by, deleted_date)
    (select 'ddp_onc_history_detail',
            onc_history_detail_id,
            json_object('onc_history_detail_id',onc_history_detail_id,'medical_record_id',medical_record_id,'date_px',date_px,'type_px',type_px,'location_px',location_px,'histology',histology,'accession_number',accession_number,'facility',facility,'phone',phone,'fax',fax,'notes',notes,'additional_values',additional_values,'additional_values_json',additional_values_json,'request',request,'fax_sent',fax_sent,'fax_sent_by',fax_sent_by,'fax_confirmed',fax_confirmed,'fax_sent_2',fax_sent_2,'fax_sent_2_by',fax_sent_2_by,'fax_confirmed_2',fax_confirmed_2,'fax_sent_3',fax_sent_3,'fax_sent_3_by',fax_sent_3_by,'fax_confirmed_3',fax_confirmed_3,'tissue_received',tissue_received,'tissue_problem',tissue_problem,'tissue_problem_text',tissue_problem_text,'tissue_problem_option',tissue_problem_option,'destruction_policy',destruction_policy,'unable_obtain',unable_obtain,'gender',gender,'unable_obtain_tissue',unable_obtain_tissue,'last_changed',last_changed,'changed_by',changed_by,'deleted',deleted),
            'andrew@broadinstitute.org',
            unix_timestamp() * 1000
     from ddp_onc_history_detail where deleted = 1);

-- insert deleted tissues
insert into deleted_object (original_table, original_primary_key, data, deleted_by, deleted_date)
    (select 'ddp_tissue',
            tissue_id,
            json_object('tissue_id',tissue_id,'onc_history_detail_id',onc_history_detail_id,'notes',notes,'count_received',count_received,'h_e_count',h_e_count,'scrolls_count',scrolls_count,'blocks_count',blocks_count,'uss_count',uss_count,'tissue_type',tissue_type,'tissue_site',tissue_site,'tumor_type',tumor_type,'h_e',h_e,'pathology_report',pathology_report,'collaborator_sample_id',collaborator_sample_id,'block_sent',block_sent,'block_id_shl',block_id_shl,'shl_work_number',shl_work_number,'scrolls_received',scrolls_received,'sk_id',sk_id,'sm_id',sm_id,'first_sm_id',first_sm_id,'sent_gp',sent_gp,'tissue_sequence',tissue_sequence,'tumor_percentage',tumor_percentage,'additional_tissue_value',additional_tissue_value,'additional_tissue_value_json',additional_tissue_value_json,'last_changed',last_changed,'changed_by',changed_by,'deleted',deleted,'expected_return',expected_return,'return_fedex_id',return_fedex_id,'return_date',return_date),
            'andrew@broadinstitute.org',
            unix_timestamp() * 1000
     from ddp_tissue where deleted = 1);

-- insert tissues that probably should have been deleted by virtue of their reference to deleted onc histories.
-- hypothesis is that these are tissues that should have been deleted when the onc history was
-- deleted, but a bug prevented proper deletion
insert into deleted_object (original_table, original_primary_key, data, deleted_by, deleted_date)
    (select 'ddp_tissue',
            tissue_id,
            json_object('tissue_id',tissue_id,'onc_history_detail_id',onc_history_detail_id,'notes',notes,'count_received',count_received,'h_e_count',h_e_count,'scrolls_count',scrolls_count,'blocks_count',blocks_count,'uss_count',uss_count,'tissue_type',tissue_type,'tissue_site',tissue_site,'tumor_type',tumor_type,'h_e',h_e,'pathology_report',pathology_report,'collaborator_sample_id',collaborator_sample_id,'block_sent',block_sent,'block_id_shl',block_id_shl,'shl_work_number',shl_work_number,'scrolls_received',scrolls_received,'sk_id',sk_id,'sm_id',sm_id,'first_sm_id',first_sm_id,'sent_gp',sent_gp,'tissue_sequence',tissue_sequence,'tumor_percentage',tumor_percentage,'additional_tissue_value',additional_tissue_value,'additional_tissue_value_json',additional_tissue_value_json,'last_changed',last_changed,'changed_by',changed_by,'deleted',deleted,'expected_return',expected_return,'return_fedex_id',return_fedex_id,'return_date',return_date),
            'andrew@broadinstitute.org',
            unix_timestamp() * 1000
     from ddp_tissue t
     where t.onc_history_detail_id in (select d.onc_history_detail_id
                                       from ddp_onc_history_detail d
                                       where d.deleted = 1)
       and (t.deleted != 1 or t.deleted is null));

-- insert deleted sm ids.  Not using accession_date column because it does not yet exist outside of dev
insert into deleted_object (original_table, original_primary_key, data, deleted_by, deleted_date)
    (select 'sm_id',
            sm_id_pk,
            json_object('sm_id_pk',sm_id_pk,'sm_id_type_id',sm_id_type_id,'tissue_id',tissue_id,'sm_id_value',sm_id_value,'deleted',deleted,'changed_by',changed_by,'last_changed',last_changed,'received_date',received_date,'received_by',received_by),
            'andrew@broadinstitute.org',
            unix_timestamp() * 1000
     from sm_id where deleted = 1);

-- insert sm ids that probably should have been deleted by virtue of their reference to deleted ddp_tissue.
-- hypothesis is that these are sm ids that should have been deleted when the tissue was
-- deleted, but a bug prevented proper deletion
insert into deleted_object (original_table, original_primary_key, data, deleted_by, deleted_date)
    (select 'sm_id',
            sm_id_pk,
            json_object('sm_id_pk',sm_id_pk,'sm_id_type_id',sm_id_type_id,'tissue_id',tissue_id,'sm_id_value',sm_id_value,'deleted',deleted,'changed_by',changed_by,'last_changed',last_changed,'received_date',received_date,'received_by',received_by),
            'andrew@broadinstitute.org',
            unix_timestamp() * 1000
     from sm_id s
     where (s.deleted is null or s.deleted != 1)
       and s.tissue_id in (select t.tissue_id from ddp_tissue t where t.deleted = 1));



-- insert sm ids that probably should have beendeleted by virtue of their
-- reference to deleted ddp_tissues that themselves reference deleted onc history rows.
-- hypothesis is that these are sm ids that should have been deleted when the tissue was
-- deleted, but a bug prevented proper deletion
insert into deleted_object (original_table, original_primary_key, data, deleted_by, deleted_date)
    (select 'sm_id',
            sm_id_pk,
            json_object('sm_id_pk',sm_id_pk,'sm_id_type_id',sm_id_type_id,'tissue_id',tissue_id,'sm_id_value',sm_id_value,'deleted',deleted,'changed_by',changed_by,'last_changed',last_changed,'received_date',received_date,'received_by',received_by),
            'andrew@broadinstitute.org',
            unix_timestamp() * 1000
     from sm_id s
     where (s.deleted is null or s.deleted != 1)
       and s.tissue_id in (select t.tissue_id from ddp_tissue t
                           where t.onc_history_detail_id in (select d.onc_history_detail_id
                                                             from ddp_onc_history_detail d
                                                             where d.deleted = 1)));

-- now do the corresponding deletes where delete = 1 or delete should have ben done previously

-- sm id direct delete
delete from sm_id where deleted = 1;

-- sm id indirect delete based on deleted tissue id
delete from sm_id where tissue_id in (select t.tissue_id from ddp_tissue t where t.deleted = 1);

-- sm id double indirect delete based on tissue that references a deleted onc history
delete from sm_id where (deleted is null or deleted != 1) and
        tissue_id in (select t.tissue_id from ddp_tissue t
                      where t.onc_history_detail_id in (select d.onc_history_detail_id
                                                        from ddp_onc_history_detail d
                                                        where d.deleted = 1));

-- tissue direct delete
delete from ddp_tissue where deleted = 1;

-- tissue indirect delete based on reference to deleted onc history
delete from ddp_tissue where (deleted is null or deleted != 1)
                         and onc_history_detail_id in (select d.onc_history_detail_id
                                                       from ddp_onc_history_detail d
                                                       where d.deleted = 1);

-- onc history direct delete
delete from ddp_onc_history_detail where deleted = 1;


-- below are some handy queries I used while developing the above script

/*
select json_object('sm_id_pk',sm_id_pk,'sm_id_type_id',sm_id_type_id,'tissue_id',tissue_id,'sm_id_value',sm_id_value,'deleted',deleted,'changed_by',changed_by,'last_changed',last_changed,'accession_date',accession_date,'received_date',received_date,'received_by',received_by)
from sm_id where deleted = 1

select json_object('tissue_id',tissue_id,'onc_history_detail_id',onc_history_detail_id,'notes',notes,'count_received',count_received,'h_e_count',h_e_count,'scrolls_count',scrolls_count,'blocks_count',blocks_count,'uss_count',uss_count,'tissue_type',tissue_type,'tissue_site',tissue_site,'tumor_type',tumor_type,'h_e',h_e,'pathology_report',pathology_report,'collaborator_sample_id',collaborator_sample_id,'block_sent',block_sent,'block_id_shl',block_id_shl,'shl_work_number',shl_work_number,'scrolls_received',scrolls_received,'sk_id',sk_id,'sm_id',sm_id,'first_sm_id',first_sm_id,'sent_gp',sent_gp,'tissue_sequence',tissue_sequence,'tumor_percentage',tumor_percentage,'additional_tissue_value',additional_tissue_value,'additional_tissue_value_json',additional_tissue_value_json,'last_changed',last_changed,'changed_by',changed_by,'deleted',deleted,'expected_return',expected_return,'return_fedex_id',return_fedex_id,'return_date',return_date)
from ddp_tissue where deleted = 1

select json_object('onc_history_detail_id',onc_history_detail_id,'medical_record_id',medical_record_id,'date_px',date_px,'type_px',type_px,'location_px',location_px,'histology',histology,'accession_number',accession_number,'facility',facility,'phone',phone,'fax',fax,'notes',notes,'additional_values',additional_values,'additional_values_json',additional_values_json,'request',request,'fax_sent',fax_sent,'fax_sent_by',fax_sent_by,'fax_confirmed',fax_confirmed,'fax_sent_2',fax_sent_2,'fax_sent_2_by',fax_sent_2_by,'fax_confirmed_2',fax_confirmed_2,'fax_sent_3',fax_sent_3,'fax_sent_3_by',fax_sent_3_by,'fax_confirmed_3',fax_confirmed_3,'tissue_received',tissue_received,'tissue_problem',tissue_problem,'tissue_problem_text',tissue_problem_text,'tissue_problem_option',tissue_problem_option,'destruction_policy',destruction_policy,'unable_obtain',unable_obtain,'gender',gender,'unable_obtain_tissue',unable_obtain_tissue,'last_changed',last_changed,'changed_by',changed_by,'deleted',deleted)
from ddp_onc_history_detail where deleted = 1

select group_concat(concat('''', column_name, ''','), column_name)
from information_schema.COLUMNS where TABLE_NAME = 'sm_id'

select group_concat(concat('''', column_name, ''','), column_name)
from information_schema.COLUMNS where TABLE_NAME = 'ddp_onc_history_detail'

select group_concat(concat('''', column_name, ''','), column_name)
from information_schema.COLUMNS where TABLE_NAME = 'ddp_tissue'

select * from deleted_object
 */
