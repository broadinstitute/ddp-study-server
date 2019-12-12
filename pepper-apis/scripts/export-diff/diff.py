#!/usr/bin/env python3

"""
A tool to compare data exports from different systems, mainly targeting DatStat and Pepper.

Concepts:

- reference: The common representation of columns and data formats that we transform all data into.
  This lets us compare apples-to-apples, and smooth out formatting oddities across systems.

- descriptions: Configuration file per system that describes where to look for input data, what the
  columns are, and how to transform data into the reference format.

- adapters: Objects that reads the description file and does the actual work of transforming data into
  the reference format. There is one adapter implementation per system.

- diff finder: Looks at data records from source and destination systems built from adapters,
  and gathers any deltas. Exact string equality is used as main criteria.

- printer: Coordinates the actual printing for the deltas in a format that humans can consume.

Consult with team to get DatStat data export files. Pepper data export files can be generated
using the DataExporterCli command-line tool, but first consult with team for database access.

IMPORTANT: make sure input data files are UTF-8 encoded and has no BOM.

Tested on Python 3.7
"""


from abc import ABC, abstractmethod
from collections import OrderedDict
from datetime import datetime as dt
from typing import Dict, Iterator, List, Optional

import csv
import difflib
import json
import os
import sys


LOVEDONE_ACTIVITY_REF_ID = "loved-one"


class Record(object):
    """Represents a data record for a participant."""

    def __init__(self, altpid: str, first_name: str, last_name: str):
        self.altpid = altpid
        self.first_name = first_name
        self.last_name = last_name
        self.activities = OrderedDict()


class LovedoneRecord(object):
    """Represents data for a loved-one survey, may or may not be from a participant themselves."""

    def __init__(self, email: str, src_first_name: str, src_last_name: str, responses: dict):
        self.email = email
        self.responses = responses
        self.src_first_name = src_first_name
        self.src_last_name = src_last_name
        self._altpid = None

    def source_name(self) -> str:
        return "{} {}".format(self.src_first_name, self.src_last_name)

    def altpid(self) -> Optional[str]:
        return self._altpid

    def set_altpid(self, altpid: str):
        self._altpid = altpid


class ExportData(object):
    """Represents a collection of participant records and loved-one records from a certain system."""

    def __init__(self, system: str, records: Dict[str, Record], lovedones: Dict[str, LovedoneRecord]):
        self.system = system
        self.records = records
        self.lovedones = lovedones


class Adapter(ABC):
    """Base class for adapters, that provides shared operations and transformations.

    At a high-level, there are a couple transformations we perform on a value
    for a column, in roughly this order:

    1. Column name mapping: The column names might be different between the
    source data and the reference format. This is where `src_id` and `ref_id`
    comes into play. If there's no `src_id`, we default to the `ref_id` and
    assume the columns have the same name.

    2. Crossing column boundaries: transformation is usually done within the
    context of a single column, but sometimes we need to look at other columns
    in order to know what to do. An example is picklist detail text columns. If
    the corresponding picklist option is not selected, it means this column's
    detail text is "orphaned" and should be ignored. To support this, we need
    to look at other columns.

    3. Column joins: sometimes the column itself does not have a value, but
    instead we are pulling data from other columns into this column. These
    external values are "joined" together to give us the value for this column.

    4. Column data type: we want values as strings since that's what we use to
    do comparisons. If the column value can be treated as a string, then we
    don't have to do any work. But sometimes it's in a format that doesn't
    match the reference. The data type tells us how to treat the source column,
    and thus letting us know how to transform the value into the common
    reference format.
    """

    @abstractmethod
    def transform(self) -> ExportData:
        """Do the entire transformation. Any required data or parameters should be set beforehand."""
        raise NotImplementedError

    def resolve_filename(self, description_filename: str, relative_filename: str) -> str:
        """Input data filenames are relative to directory of description file."""
        dirname = os.path.dirname(description_filename)
        return os.path.join(dirname, relative_filename)

    def load_csv_rows(self, filename: str, delimiter = ',') -> Iterator[dict]:
        """Provides csv rows as dictionaries keyed by their column names."""
        with open(filename) as csv_file:
            reader = csv.DictReader(csv_file, delimiter=delimiter)
            for row in reader:
                yield row

    def transform_columns(self, columns: List[dict], row: dict, participant_row = None) -> Dict[str, str]:
        """Transforms data row into the reference format by using column rules."""
        responses = OrderedDict()
        for col in columns:
            ref_id = col['ref_id']
            src_id = col['src_id'] if 'src_id' in col else ref_id
            if self.should_ignore_column_value(col, row):
                responses[ref_id] = ''
            else:
                raw_value = self.pull_src_value(src_id, col, row, participant_row)
                value = self.transform_value(col, raw_value)
                responses[ref_id] = value
        return responses

    def should_ignore_column_value(self, col: dict, row: dict) -> bool:
        """Check if we should ignore value from this column."""
        if 'check_selected_option' in col:
            [question_id, option_id] = col['check_selected_option'].split('.')
            choice_raw_value = row.get(question_id, '')
            if option_id not in choice_raw_value.split(','):
                # This column has orphaned detail text for option, let's ignore it
                return True
        return False

    def pull_src_value(self, src_id: str, col: dict, row: dict, participant_row: dict) -> str:
        """Grab column value from source row data, taking care of joins and swapping participant data."""
        if col.get('participant_data_as_source') and participant_row is not None:
            # Use participant data instead of activity data if it's requested and available
            return self.pull_src_value_from_participant(src_id, col, row, participant_row)
        elif 'join_src_ids' in col:
            return self.handle_column_joins(col, row)
        else:
            return row[src_id]

    def pull_src_value_from_participant(self, src_id: str, col: dict, row: dict, participant_row: dict) -> str:
        """Grab value from participant data instead of source activity data, handling fallback if desired."""
        if 'participant_join_src_ids' in col:
            join_values = [participant_row.get(sid, '') for sid in col['participant_join_src_ids']]
            if any(join_values):
                # Participant row has values to join together, let's use it
                return col['join_delimiter'].join(join_values)
            elif col.get('participant_join_fallback_if_empty') and 'join_src_ids' in col:
                # Participant row has no values, fallback to joining on regular row
                return self.handle_column_joins(col, row)
            else:
                # Join values are empty, but no fallback, so just join the empties
                return col['join_delimiter'].join(join_values)
        else:
            # No joins, just pulling from regular column
            return participant_row[src_id]

    def handle_column_joins(self, col: dict, row: dict) -> str:
        """Grab multiple column values from source activity data and concatenate them."""
        delimiter = col['join_delimiter']
        join_values = [row.get(sid, '') for sid in col['join_src_ids']]
        if col.get('join_trim_if_all_empty'):
            return delimiter.join(join_values) if any(join_values) else ''
        else:
            return delimiter.join(join_values)

    def transform_value(self, column: dict, value: str) -> str:
        """Transforms individual column value by following rules for column type."""
        if not value:
            return ''

        col_type = column['type']

        if col_type == 'us_timestamp':
            # Turn American timestamp format into ISO-8601 format w/o timezone indicator
            return dt.strptime(value, '%m/%d/%Y %H:%M:%S').isoformat()
        elif col_type == 'utc_timestamp':
            # Strip timezone indicator from ISO-8601 timestamp
            return value.replace('UTC', '').replace('Z', '')
        elif col_type == 'choice':
            return self.transform_choice(column, value)
        elif col_type == 'multi_line':
            # Flatten multi-line into single line.
            return value.replace('\n', '  ').replace('\t', ' ')
        elif col_type == 'json_list':
            return self.transform_json_list(column, value)
        elif col_type == 'relation':
            return self.transform_relation(column, value)
        elif col_type == 'us_date':
            # Zero pad the date in American format
            return dt.strptime(value, '%m/%d/%Y').strftime('%m/%d/%Y')
        elif col_type == 'date_month':
            # Zero pad the month
            return dt.strptime(value, '%m').strftime('%m')
        elif col_type == 'binary':
            # Turn binary into boolean string
            return 'true' if value == '1' else 'false'
        elif col_type == 'bool':
            # Normalize to lowercase
            return value.lower()
        elif col_type == 'str':
            return value
        else:
            raise ValueError(f"column type {col_type} not supported")

    def transform_choice(self, column: dict, value: str) -> str:
        """Transforms a list of choice/picklist options by sorting them."""
        options = ','.join(sorted(value.split(',')))
        if 'option_ref_ids' in column:
            for substitution in column['option_ref_ids']:
                options = options.replace(substitution['src_id'], substitution['ref_id'])
        return options

    def transform_json_list(self, column: dict, value: str) -> str:
        """Transforms json array by picking out specified keys from json objects and flattening them."""
        result = []
        array = json.loads(value)
        for obj in array:
            entry = [obj.get(key) for key in column['keys']]
            values = [str(val if val is not None else '') for val in entry]
            result.append(';'.join(values))
        return '|'.join(result)

    def transform_relation(self, column: dict, value: str) -> str:
        """Transforms the relation-to index into option name, mainly used in lovedone."""
        mapping = {
            '1': 'PARENT',
            '2': 'SIBLING',
            '3': 'CHILD',
            '4': 'AUNT_UNCLE',
            '5': 'FRIEND',
            '6': 'OTHER',
            '7': 'SPOUSE'
        }
        return mapping[value]


class PepperAdapter(Adapter):
    """Adapter to transform data from Pepper, using a single data file."""

    def __init__(self, description_filename: str, description_json: dict):
        self.filename = description_filename
        self.desc = description_json

    def transform(self) -> ExportData:
        system = self.desc['system']
        records = OrderedDict()
        lovedones = OrderedDict()

        src_file = self.resolve_filename(self.filename, self.desc['source_file'])
        rows_iter = self.load_csv_rows(src_file)

        key = self.desc.get('lovedone_keyed_by', 'email')
        if key == 'email':
            print(f"{system}: lovedone records will be identified using '{key}'")
            key_fn = lambda record: record.email
        elif key == 'source_name':
            print(f"{system}: lovedone records will be identified using '{key}'")
            key_fn = lambda record: record.source_name()
        else:
            print(f"{system}: '{key}' as lovedone identifier not supported, defaulting to 'email'")
            key_fn = lambda record: record.email

        for row in rows_iter:
            if not row['legacy_altpid']:
                guid = row['participant_guid']
                print(f"{system}: participant with guid '{guid}' is not a migrated participant, skipping")
                continue

            record = self.transform_record(row)
            shortid = row['legacy_shortid']

            if not shortid and record.altpid.startswith('999.'):
                # The way to tell loved-one is if shortid is missing and altpid matches pattern
                rec = self.extract_lovedone_record(row, record)
                lovedones[key_fn(rec)] = rec
                print(f"{system}: processed lovedone record for '{rec.email}' with" \
                      f" altpid '{rec.altpid()}' and source name '{rec.source_name()}'")
            else:
                records[record.altpid] = record
                print(f"{system}: processed participant record for '{record.altpid}'")
                if LOVEDONE_ACTIVITY_REF_ID in record.activities:
                    rec = self.extract_lovedone_record(row, record)
                    lovedones[key_fn(rec)] = rec
                    print(f"{system}: moved lovedone record from altpid '{rec.altpid()}'" \
                          f" using email '{rec.email}' and source name '{rec.source_name()}'")

        return ExportData(system, records, lovedones)

    def transform_record(self, row: dict) -> Record:
        record = Record(row['legacy_altpid'], row['first_name'], row['last_name'])
        for activity in self.desc['activities']:
            ref_id = activity['ref_id']
            src_id = activity['src_id']
            instance_guid = row[src_id]
            if not instance_guid:
                # Participant does not have an instance for this activity, skipping
                continue
            responses = self.transform_columns(activity['columns'], row)
            record.activities[ref_id] = responses
        return record

    def extract_lovedone_record(self, row: dict, record: Record) -> LovedoneRecord:
        email = row['email']
        responses = record.activities.pop(LOVEDONE_ACTIVITY_REF_ID)

        # Don't have source name in Pepper, so put the participant name instead.
        # For non-participants, this will give the right thing.
        lovedone = LovedoneRecord(email, record.first_name, record.last_name, responses)
        lovedone.set_altpid(record.altpid)

        return lovedone


class DatStatAdapter(Adapter):
    """Adapter to transform data from DatStat, using multiple data files."""

    def __init__(self, description_filename: str, description_json: dict):
        self.filename = description_filename
        self.desc = description_json
        self.system = self.desc['system']

    def transform(self) -> ExportData:
        records = OrderedDict()
        lovedones = OrderedDict()

        # Load datstat participant data so we can substitute in as source data later, i.e. for release address
        participants = OrderedDict()
        participant_file = self.resolve_filename(self.filename, self.desc['participant_file'])
        delimiter = self.desc.get('delimiter', ',')
        rows_iter = self.load_csv_rows(participant_file, delimiter=delimiter)
        for row in rows_iter:
            altpid = row['DATSTAT_ALTPID']
            participants[altpid] = row

        for activity in self.desc['activities']:
            ref_id = activity['ref_id']

            src_file = self.resolve_filename(self.filename, activity['source_file'])
            delimiter = activity.get('delimiter', ',')
            rows_iter = self.load_csv_rows(src_file, delimiter=delimiter)

            if ref_id == LOVEDONE_ACTIVITY_REF_ID:
                lovedones = self.transform_lovedones(activity, rows_iter)
                continue

            for row in rows_iter:
                altpid = row['DATSTAT_ALTPID']
                participant_row = participants.get(altpid)
                responses = self.transform_columns(activity['columns'], row, participant_row=participant_row)
                if altpid in records:
                    record = records[altpid]
                else:
                    first_name = participant_row['DATSTAT_FIRSTNAME'] if participant_row else ''
                    last_name = participant_row['DATSTAT_LASTNAME'] if participant_row else ''
                    record = Record(altpid, first_name, last_name)
                    records[altpid] = record
                record.activities[ref_id] = responses
                print(f"{self.system}: processed participant record for '{altpid}' in activity {ref_id}")

        return ExportData(self.system, records, lovedones)

    def transform_lovedones(self, activity: dict, rows: Iterator[dict]) -> Dict[str, LovedoneRecord]:
        records = self.process_lovedone_records(activity, rows)
        key = self.desc.get('lovedone_keyed_by', 'email')
        if key == 'email':
            print(f"{self.system}: lovedone records will be identified using '{key}'")
            return records
        elif key == 'source_name':
            print(f"{self.system}: lovedone records will be identified using '{key}'")
            container = OrderedDict()
            for lovedone in records.values():
                container[lovedone.source_name()] = lovedone
            return container
        else:
            print(f"{self.system}: '{key}' as lovedone identifier not supported, defaulting to 'email'")
            return records

    def process_lovedone_records(self, activity: dict, rows: Iterator[dict]) -> Dict[str, LovedoneRecord]:
        records = OrderedDict()
        for row in rows:
            email = row['SOURCE_EMAIL']
            responses = self.transform_columns(activity['columns'], row)
            record = LovedoneRecord(email, row['SOURCE_FIRST_NAME'], row['SOURCE_LAST_NAME'], responses)
            if email in records:
                # Already has loved-one with this email, decide to keep or replace
                curr_record = records[email]
                if self.should_replace_dup_lovedone(curr_record.responses, responses):
                    records[email] = record
                    print(f"{self.system}: replaced lovedone record for '{email}'" \
                          f" and using source name '{record.source_name()}'")
                else:
                    print(f"{self.system}: kept existing lovedone record for '{email}'" \
                          f" with source name '{curr_record.source_name()}'")
            else:
                records[email] = record
                print(f"{self.system}: processed lovedone record for '{email}'" \
                      f" with source name '{record.source_name()}'")
        return records

    def should_replace_dup_lovedone(self, old_responses: dict, new_responses: dict) -> bool:
        old_updated_ts = dt.fromisoformat(old_responses['updated_at'])
        if old_responses['completed_at']:
            old_completed_ts = dt.fromisoformat(old_responses['completed_at'])
        else:
            old_completed_ts = None

        new_updated_ts = dt.fromisoformat(new_responses['updated_at'])
        if new_responses['completed_at']:
            new_completed_ts = dt.fromisoformat(new_responses['completed_at'])
        else:
            new_completed_ts = None

        # Resolving duplicates: latest completed, or latest updated
        if old_completed_ts and new_completed_ts:
            replace = True if old_completed_ts < new_completed_ts else False
        elif old_completed_ts and not new_completed_ts:
            replace = False
        elif not old_completed_ts and new_completed_ts:
            replace = True
        elif old_updated_ts < new_updated_ts:
            replace = True
        else:
            replace = False

        return replace


class Delta(object):

    def __init__(self, rec_key: str, activity_ref_id: str, column_ref_id: str, src_val: str, dest_val: str):
        self.rec_key = rec_key
        self.act_ref_id = activity_ref_id
        self.col_ref_id = column_ref_id
        self.src_val = src_val
        self.dest_val = dest_val

    def print_diff_block(self):
        df = difflib.Differ()
        src_line = [self.src_val + '\n']
        dest_line = [self.dest_val + '\n']
        diff_lines = df.compare(src_line, dest_line)
        print(f"[diff] {self.rec_key}\t{self.act_ref_id}\t{self.col_ref_id}")
        print(''.join(diff_lines), end='')


def find_diffs(src, dest) -> List[Delta]:
    deltas = []

    print("\nstarting comparison for participant records")
    for altpid, src_rec in src.records.items():
        dest_rec = dest.records.get(altpid)
        if not dest_rec:
            print(f"could not find '{altpid}' in destination participant data")
            continue

        src_act_ids = list(src_rec.activities.keys())
        dest_act_ids = list(dest_rec.activities.keys())
        if len(src_act_ids) != len(dest_act_ids):
            print(f"different number of activities: " \
                  f"altpid={altpid} src={','.join(src_act_ids)} dest={','.join(dest_act_ids)}")

        if src_rec.first_name != dest_rec.first_name:
            deltas.append(Delta(altpid, 'participant', 'first_name', src_rec.first_name, dest_rec.first_name))

        if src_rec.last_name != dest_rec.last_name:
            deltas.append(Delta(altpid, 'participant', 'last_name', src_rec.last_name, dest_rec.last_name))

        for act_id, src_act in src_rec.activities.items():
            if act_id in dest_rec.activities:
                dest_act = dest_rec.activities[act_id]
            else:
                print(f"destination participant record '{altpid}' does not have activity with ref_id {act_id}")
                continue

            for col_id in src_act:
                src_val = str(src_act[col_id])
                dest_val = str(dest_act.get(col_id, f"<missing column '{col_id}'>"))
                if src_val != dest_val:
                    deltas.append(Delta(altpid, act_id, col_id, src_val, dest_val))

    print("\nstarting comparison for lovedone records")
    for key, src_rec in src.lovedones.items():
        dest_rec = dest.lovedones.get(key)
        if not dest_rec:
            print(f"could not find '{key} <{src_rec.email}>' in destination lovedone data")
            continue

        for col_id in src_rec.responses:
            src_val = str(src_rec.responses[col_id])
            dest_val = str(dest_rec.responses.get(col_id, f"<missing column '{col_id}'>"))
            if src_val != dest_val:
                deltas.append(Delta(email, LOVEDONE_ACTIVITY_REF_ID, col_id, src_val, dest_val))

    return deltas


def pretty_print_deltas(deltas: List[Delta]):
    if deltas:
        print("\nfound differences:")
        print('')
        for delta in deltas:
            delta.print_diff_block()
            print('')
    else:
        print("\nno differences found")


def process_description(filename: str) -> ExportData:
    with open(filename) as desc_file:
        desc = json.load(desc_file)

    if desc['system'] == 'pepper':
        return PepperAdapter(filename, desc).transform()
    elif desc['system'] == 'datstat':
        return DatStatAdapter(filename, desc).transform()
    else:
        raise ValueError(f"system '{desc['system']}' is not supported")


def print_usage():
    print("usage: diff.py SOURCE_DATA_DESCRIPTION_FILE DESTINATION_DATA_DESCRIPTION_FILE")


def main(args):
    if len(args) != 2:
        print_usage()
        sys.exit(1)

    src_desc_file = args[0]
    dest_desc_file = args[1]

    try:
        src_data = process_description(src_desc_file)
        dest_data = process_description(dest_desc_file)
    except ValueError as err:
        print(err)
        sys.exit(1)

    deltas = find_diffs(src_data, dest_data)
    pretty_print_deltas(deltas)


if __name__ == '__main__':
    main(sys.argv[1:])
