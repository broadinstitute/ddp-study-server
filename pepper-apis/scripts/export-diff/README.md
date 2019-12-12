## Introduction

`diff.py` is a tool meant to help data migration of DatStat/Gen2 studies onto
the Pepper platform, by ingesting data from both systems and highlighting
differences in the data. The comparison results can help guide data migration
and in finding bugs.

At a high-level, the tool takes csv exports from both systems, transforms the
data into a common format, then compares data in corresponding columns. The
results are displayed by showing both values, along with indicators of where
differences arise. This output is similar to what you get from `git diff`, but
with more fine-grain character-based indicators.

## Getting Started

These steps should be performed before running the tool:

1. **Gather Data** We first need to get the data. Consult with a team member
   who has access to DatStat to generate raw csv export files. For Pepper data,
   you can run the `DataExporterCli` tool manually, or grab an export file from
   the appropriate Google Bucket.

2. **Prepping Data** The Pepper export file should be ready to go, but the
   DatStat export files will need some prepping. Two things need to happen:

    a) Files should be encoded in UTF-8. If you're on Mac/Linux, try the
    following command:

    ```
    $ iconv -f latin1 -t utf-8 original_file.txt > output.txt
    ```

    b) Files in UTF-8 should not have BOM. It's not necessary for UTF-8, and it
    messes up reading in the file. Run the `file` command to see if a file has
    BOM or not. And there's [lots of options][rm-bom] to remove the BOM.

3. **Update Descriptions** Open up the description files and update the file
   paths to point to the export files gathered earlier. If we're comparing data
   with emails unchanged, we might consider changing `lovedone_keyed_by` to
   `email` for more accurate comparisons of loved-one data. The rest of the
   descriptions shouldn't need to change.

Now we're ready to run the tool!

```
$ ./diff.py path/to/datstat_desc.json path/to/pepper_desc.json > results.txt
```

The tool will write results to standard output, so you can save it to a file or
pipe it through `tee`.

## Analyzing Results

Once we have the results, we can see if there are differences (or if there's
false positives). By default, the tool prints out information on it's progress,
like when it's processing records, so those logs should help in understanding
the results. Any differences found will be displayed using a format similar to
`git diff`, so it should be straightforward to understand.

At some point, you might want to interactively inspect the input data more
closely, either to understand the diff results or to verify that it's right.
Excel is an easy tool to do this with. Some tips on Excel:

* Use the Excel importer instead of simply opening the export file(s). This is
  under the menu `File > Import...`, and it gives you better control of whether
  to use comma or tabs, and the data type of each column.

* For best results, import each column as the "text" data type. If we don't,
  Excel will try to be smart and automatically format the data. Sometimes this
  is undesirable, i.e. for timestamp columns Excel will format it _without_
  seconds.

* Excel doesn't handle importing csv properly when a column value have newline
  characters. For the Pepper export, try to "flatten" the multi-line values
  before importing. You can use the `flatten_pepper.py` script for this.

  ```
  $ ./flatten_pepper.py path/to/pepper_data.csv output.csv
  ```

[rm-bom]: https://unix.stackexchange.com/questions/381230/how-can-i-remove-the-bom-from-a-utf-8-file
