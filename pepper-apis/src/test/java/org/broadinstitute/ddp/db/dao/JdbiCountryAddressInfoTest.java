package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.address.CountryAddressInfo;
import org.broadinstitute.ddp.model.address.CountryAddressInfoSummary;
import org.junit.Test;

public class JdbiCountryAddressInfoTest extends TxnAwareBaseTest {

    @Test
    public void testGetAllCountries() {
        TransactionWrapper.useTxn(handle -> {
            JdbiCountryAddressInfo dao = handle.attach(JdbiCountryAddressInfo.class);
            List<CountryAddressInfoSummary> summaries = dao.getAllCountryAddressInfoSummaries();
            assertNotNull(summaries);
            assertFalse(summaries.isEmpty());
            List<CountryAddressInfoSummary> summariesWithoutName = summaries.stream()
                    .filter((summary) -> summary.getName() == null).collect(Collectors.toList());
            assertTrue(summariesWithoutName.isEmpty());
            List<CountryAddressInfoSummary> summariesWithoutCode = summaries.stream()
                    .filter((summary) -> summary.getName() == null).collect(Collectors.toList());
            assertTrue(summariesWithoutCode.isEmpty());
            Optional<CountryAddressInfoSummary> canada = summaries.stream()
                    .filter(summary -> summary.getName().equals("Canada")).findFirst();
            assertTrue(canada.isPresent());
            assertEquals("CA", canada.get().getCode());
        });
    }

    @Test
    public void testGetAllOrderedSummaries() {
        TransactionWrapper.useTxn(handle -> {
            JdbiCountryAddressInfo dao = handle.attach(JdbiCountryAddressInfo.class);
            List<CountryAddressInfoSummary> summaries = dao.getAllOrderedSummaries();
            assertNotNull(summaries);
            assertFalse(summaries.isEmpty());

            assertEquals(summaries.get(0).getCode(), "US");
            assertEquals(summaries.get(1).getCode(), "CA");

            assertEquals(summaries.get(2).getName(), "Afghanistan");
            assertEquals(summaries.get(summaries.size() - 1).getName(), "Åland Islands");
        });
    }

    @Test
    public void testGetUsCountryDetails() {
        TransactionWrapper.useTxn(handle -> {
            JdbiCountryAddressInfo dao = handle.attach(JdbiCountryAddressInfo.class);
            Optional<CountryAddressInfo> info = dao.getCountryAddressInfo("US");
            assertTrue(info.isPresent());
            CountryAddressInfo us = info.get();
            assertTrue(us.getName().startsWith("United States"));
            assertEquals("State", us.getSubnationalDivisionTypeName());
            assertEquals("Zip Code", us.getPostalCodeLabel());
            assertNotNull(us.getPostalCodeRegex());
            assertEquals("US", us.getCode());
            assertNull(us.getStateCode());
            // 51 including DC
            assertEquals(51, us.getSubnationalDivisions().size());
        });
    }

    @Test
    public void testGetCountryNotFound() {
        TransactionWrapper.useTxn(handle -> {
            JdbiCountryAddressInfo dao = handle.attach(JdbiCountryAddressInfo.class);
            Optional<CountryAddressInfo> info = dao.getCountryAddressInfo("DOESNOTEXIST");
            assertFalse(info.isPresent());
        });
    }

    @Test
    public void testGetWithNoSubnationalDivisions() {
        TransactionWrapper.useTxn(handle -> {
            JdbiCountryAddressInfo dao = handle.attach(JdbiCountryAddressInfo.class);
            Optional<CountryAddressInfo> info = dao.getCountryAddressInfo("PR");
            assertTrue(info.isPresent());
            assertEquals(0, info.get().getSubnationalDivisions().size());
            assertEquals("PR", info.get().getStateCode());
        });
    }
}
