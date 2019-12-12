#!/usr/bin/env python3
#
# Quick script to "flatten" multi-line string values into single-line values.

import csv
import sys

in_filename = sys.argv[1]
out_filename = sys.argv[2]

with open(in_filename) as in_file:
    reader = csv.reader(in_file)
    rows = [row for row in reader]

flattened = [[v.replace('\n', '  ').replace('\t', ' ') for v in row] for row in rows]

with open(out_filename, 'w', newline='') as out_file:
    writer = csv.writer(out_file, dialect='unix', quoting=csv.QUOTE_MINIMAL)
    writer.writerows(flattened)

