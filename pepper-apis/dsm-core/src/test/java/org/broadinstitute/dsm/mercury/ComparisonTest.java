package org.broadinstitute.dsm.mercury;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.db.dto.mercury.ClinicalOrderDto;
import org.junit.Assert;
import org.junit.Test;

public class ComparisonTest {
    @Test
    public void testLongComparison() {
        Long[] array = new Long[] {2L, 5L, 1L, 4L};

        List<Long> arrayList = List.of(array);
        List<Long> sortedArrayList = arrayList.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());

        Assert.assertEquals(1L, (long) sortedArrayList.get(3));
        Assert.assertEquals(2L, (long) sortedArrayList.get(2));
        Assert.assertEquals(4L, (long) sortedArrayList.get(1));
        Assert.assertEquals(5L, (long) sortedArrayList.get(0));

        ArrayList<ClinicalOrderDto> clinicalOrderDtoArrayList = new ArrayList<>();
        clinicalOrderDtoArrayList.add(new ClinicalOrderDto("FAKE_SHORT_ID", "FAKE_SAMPLE", "FAKE_ORDER", "FAKE_STATUS",
                1L, 1L, null, "FAKE_TYPE", 1));
        clinicalOrderDtoArrayList.add(new ClinicalOrderDto("FAKE_SHORT_ID_2", "FAKE_SAMPLE_2", "FAKE_ORDER_2", "FAKE_STATUS",
                2L, 2L, null, "FAKE_TYPE", 2));

        List<ClinicalOrderDto> clinicalOrderDtoList = clinicalOrderDtoArrayList.stream()
                .sorted(Comparator.comparingLong(ClinicalOrderDto::getOrderDate).reversed()).collect(Collectors.toList());
        Assert.assertEquals(clinicalOrderDtoList.get(0).getOrderDate(), 2L);
        Assert.assertEquals(clinicalOrderDtoList.get(1).getOrderDate(), 1L);
    }

}
