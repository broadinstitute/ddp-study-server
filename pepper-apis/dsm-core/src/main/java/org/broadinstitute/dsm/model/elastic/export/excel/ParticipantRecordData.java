package org.broadinstitute.dsm.model.elastic.export.excel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.ParticipantColumn;
import org.broadinstitute.dsm.model.elastic.export.excel.renderer.ValueProvider;
import org.broadinstitute.dsm.model.elastic.export.excel.renderer.ValueProviderFactory;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperDto;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;
import org.broadinstitute.dsm.statics.DBConstants;

public class ParticipantRecordData {
    private final Map<Alias, List<Filter>> columnAliasEsPathMap;
    private final List<String> headerNames = new ArrayList<>();
    private List<Integer> columnSizes = new ArrayList<>();
    private ValueProviderFactory valueProviderFactory = new ValueProviderFactory();
    public ParticipantRecordData(Map<Alias, List<Filter>> columnAliasEsPathMap) {
        this.columnAliasEsPathMap = columnAliasEsPathMap;
    }

    public List<List<String>> processData(ParticipantWrapperResult participantData, boolean isCountPhase) {
        List<ParticipantRecord> participantRecords = new ArrayList<>();
        for (ParticipantWrapperDto participant : participantData.getParticipants()) {
            ParticipantRecord participantRecord = new ParticipantRecord();
            Map<String, Object> esDataAsMap = participant.getEsDataAsMap();
            for (Map.Entry<Alias, List<Filter>> aliasListEntry : columnAliasEsPathMap.entrySet()) {
                Alias key = aliasListEntry.getKey();
                for (Filter column : aliasListEntry.getValue()) {
                    ParticipantColumn participantColumn = column.getParticipantColumn();
                    String esPath = getEsPath(key, participantColumn);
                    ValueProvider valueProvider = valueProviderFactory.getValueProvider(column.getType());
                    Collection<String> renderedValues = valueProvider.getValue(esPath, esDataAsMap, aliasListEntry.getKey(), column);
                    ColumnValue columnValue = new ColumnValue(key, renderedValues);
                    participantRecord.add(columnValue);
                }
            }
            if (isCountPhase) {
                initOrUpdateSizes(participantRecord);
            } else {
                participantRecords.add(participantRecord);
            }
        }
        return getRowData(participantRecords);

    }

    public List<String> getHeader() {
        int i = 0;
        for (Map.Entry<Alias, List<Filter>> aliasListEntry : columnAliasEsPathMap.entrySet()) {
            headerNames.addAll(getColumnNamesFor(aliasListEntry, columnSizes.subList(i, i + aliasListEntry.getValue().size())));
            i+= aliasListEntry.getValue().size();
        }
        return headerNames;
    }

    private List<List<String>> getRowData(List<ParticipantRecord> participantRecords) {
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

    private Collection<String> getColumnNamesFor(Map.Entry<Alias, List<Filter>> aliasColumns, List<Integer> sizes) {
        List<Filter> columnsList = aliasColumns.getValue();
        List<String> columns = new ArrayList<>();
        int sameAliasSize = sizes.get(0);
        if (aliasColumns.getKey() != Alias.ACTIVITIES) {
            IntStream.rangeClosed(1, sizes.get(0)).forEach(value ->
                    columns.addAll(columnsList.stream().map(column -> String.format("%s %s", column.getParticipantColumn().getDisplay(),
                                    aliasColumns.getKey().isCollection() && sameAliasSize > 1? value : StringUtils.EMPTY))
                            .collect(Collectors.toList())));
        } else {
            return IntStream.range(0, columnsList.size()).mapToObj(i -> Pair.of(columnsList.get(i), sizes.get(i)))
                    .map(entry -> IntStream.rangeClosed(1, entry.getValue())
                            .mapToObj(currentIndex -> String.format("%s %s", entry.getKey().getParticipantColumn().getDisplay(),
                                    aliasColumns.getKey().isCollection() && entry.getValue() > 1? currentIndex : StringUtils.EMPTY))
                            .collect(Collectors.toList())).flatMap(Collection::stream).collect(Collectors.toList());
        }

        return columns;
    }


    private String getEsPath(Alias alias, ParticipantColumn column) {
        if (alias == Alias.ACTIVITIES) {
            return alias.getValue();
        }
        if (alias.isJson()) {
            return alias.getValue();
        }
        return alias.getValue().isEmpty() ? column.getName() : alias.getValue() + DBConstants.ALIAS_DELIMITER + column.getName();
    }

}
