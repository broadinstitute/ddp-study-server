package org.broadinstitute.dsm.model.elastic.export.excel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.ParticipantColumn;
import org.broadinstitute.dsm.model.elastic.sort.Alias;

public class ParticipantRecordData {
    private final Map<Alias, List<ParticipantColumn>> columnAliasEsPathMap;
    private final List<ParticipantRecord> participantRecords = new ArrayList<>();
    private final List<String> headerNames = new ArrayList<>();
    private List<Integer> columnSizes = new ArrayList<>();

    public ParticipantRecordData(Map<Alias, List<ParticipantColumn>> columnAliasEsPathMap) {
        this.columnAliasEsPathMap = columnAliasEsPathMap;
    }

    public void addParticipant(ParticipantRecord participantRecord) {
        this.participantRecords.add(participantRecord);
        initOrUpdateSizes(participantRecord);
    }

    public List<String> getHeader() {
        int i = 0;
        for (Map.Entry<Alias, List<ParticipantColumn>> aliasListEntry : columnAliasEsPathMap.entrySet()) {
            headerNames.addAll(getColumnNamesFor(aliasListEntry, columnSizes.get(i)));
            i+= aliasListEntry.getValue().size();
        }
        return headerNames;
    }

    public List<List<String>> getRowData() {
        List<List<String>> rowValues = new ArrayList<>();
        participantRecords.forEach(record -> rowValues.add(record.transposeAndFlatten(columnSizes)));
        return rowValues;
    }

    private void initOrUpdateSizes(ParticipantRecord participantRecord) {
        List<ColumnValue> combinedValues = participantRecord.getValues();
        if (columnSizes.isEmpty()) {
            columnSizes = combinedValues.stream().map(ColumnValue::getColumnsSize)
                    .collect(Collectors.toList());
        } else {
            for (int i = 0; i < columnSizes.size(); i++) {
                columnSizes.set(i, Math.max(columnSizes.get(i), combinedValues.get(i).getColumnsSize()));
            }
        }
    }

    private Collection<String> getColumnNamesFor(Map.Entry<Alias, List<ParticipantColumn>> aliasColumns, int size) {
        List<String> columns = new ArrayList<>();
        IntStream.rangeClosed(1, size).forEach(value ->
                columns.addAll(aliasColumns.getValue().stream().map(column -> String.format("%s %s", column.getDisplay(),
                                aliasColumns.getKey().isCollection() && size > 1? value : StringUtils.EMPTY))
                        .collect(Collectors.toList())));
        return columns;
    }
}
