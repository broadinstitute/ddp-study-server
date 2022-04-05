package org.broadinstitute.dsm.model.elastic.export.excel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.broadinstitute.dsm.model.elastic.sort.Alias;

public class ParticipantRecord {
    private final List<ColumnValue> values = new ArrayList<>();
    public List<String> transposeAndFlatten(List<Integer> columnSizes) {
        fillWithEmptyStringsIfNeeded(columnSizes);
        List<ColumnValue> collectionValues = values.stream().filter(ColumnValue::isCollection).collect(Collectors.toList());
        Stream<String> singleValues = values.stream().filter(Predicate.not(ColumnValue::isCollection))
                .map(columnValue -> columnValue.getObject().toString());
        final int size = collectionValues.stream().mapToInt(ColumnValue::getColumnsSize).max().orElse(-1);
        Map<Alias, List<Iterator<Object>>> aliasIterators = new LinkedHashMap<>();
        collectionValues.stream().map(columnValue -> Map.entry(columnValue.getAlias(), columnValue.iterator()))
                .forEach(entry -> aliasIterators.computeIfAbsent(entry.getKey(), e -> new ArrayList<>()).add(entry.getValue()));
        Stream.Builder<String> rowStream = Stream.builder();
        singleValues.forEach(rowStream::add);
        aliasIterators.forEach((key, value) ->
                IntStream.range(0, size)
                        .mapToObj(n -> value.stream()
                                .filter(Iterator::hasNext)
                                .map(it -> it.next().toString())
                                .collect(Collectors.toList()))
                        .flatMap(Collection::stream).forEach(rowStream::add));
        return rowStream.build().collect(Collectors.toList());
    }

    private void fillWithEmptyStringsIfNeeded(List<Integer> columnSizes) {
        for (int i = 0; i < values.size(); i++) {
            int currentSize = values.get(i).getColumnsSize();
            int requiredSize = columnSizes.get(i);
            if (currentSize < requiredSize) {
                values.get(i).appendEmptyStrings(requiredSize - currentSize);
            }
        }
    }

    public void add(ColumnValue columnValue) {
        values.add(columnValue);
    }

    public List<ColumnValue> getValues() {
        return values;
    }

}
