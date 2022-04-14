package org.broadinstitute.ddp.export.collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.model.activity.definition.InstitutionComponentDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianComponentDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.junit.Before;
import org.junit.Test;

public class MedicalProviderFormatterTest {

    private MedicalProviderFormatter fmt;
    private Template tmpl;

    @Before
    public void setup() {
        fmt = new MedicalProviderFormatter();
        tmpl = Template.text("template");
    }

    @Test
    public void testMappings_singleProperty() {
        Map<String, Object> actual = fmt.mappings(new PhysicianComponentDef(true, tmpl, tmpl, tmpl, InstitutionType.PHYSICIAN,
                true, false));
        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertTrue(actual.containsKey("PHYSICIAN"));
        assertEquals("text", ((Map) actual.get("PHYSICIAN")).get("type"));

        actual = fmt.mappings(new InstitutionComponentDef(true, tmpl, tmpl, tmpl, InstitutionType.INITIAL_BIOPSY, true, false));
        assertEquals(1, actual.size());
        assertTrue(actual.containsKey("INITIAL_BIOPSY"));
        assertEquals("text", ((Map) actual.get("INITIAL_BIOPSY")).get("type"));

        actual = fmt.mappings(new InstitutionComponentDef(true, tmpl, tmpl, tmpl, InstitutionType.INSTITUTION, true, false));
        assertEquals(1, actual.size());
        assertTrue(actual.containsKey("INSTITUTION"));
        assertEquals("text", ((Map) actual.get("INSTITUTION")).get("type"));
    }

    @Test
    public void testHeaders_singleColumn() {
        List<String> actual = fmt.headers(new PhysicianComponentDef(true, tmpl, tmpl, tmpl, InstitutionType.PHYSICIAN, true, false));
        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("PHYSICIAN", actual.get(0));

        actual = fmt.headers(new InstitutionComponentDef(true, tmpl, tmpl, tmpl, InstitutionType.INITIAL_BIOPSY, true, false));
        assertEquals(1, actual.size());
        assertEquals("INITIAL_BIOPSY", actual.get(0));

        actual = fmt.headers(new InstitutionComponentDef(true, tmpl, tmpl, tmpl, InstitutionType.INSTITUTION, true, false));
        assertEquals(1, actual.size());
        assertEquals("INSTITUTION", actual.get(0));
    }

    @Test
    public void testCollect_nullProviders() {
        Map<String, String> actual = fmt.collect(InstitutionType.PHYSICIAN, null);

        assertNotNull(actual);
        assertTrue(actual.isEmpty());
    }

    @Test
    public void testCollect_noProviders() {
        Map<String, String> actual = fmt.collect(InstitutionType.PHYSICIAN, Collections.emptyList());

        assertNotNull(actual);
        assertTrue(actual.isEmpty());
    }

    @Test
    public void testCollect_singleProviderFieldsSemicolonSeparated() {
        Map<String, String> actual = fmt.collect(InstitutionType.PHYSICIAN, Arrays.asList(
                new MedicalProviderDto(1L, "a", 1L, 1L, InstitutionType.PHYSICIAN,
                        "inst a", "dr. smith, jr.", "boston", "ma", null, null, null, null)));

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("dr. smith, jr.;inst a;boston;ma", actual.get("PHYSICIAN"));
    }

    @Test
    public void testCollect_filtersOnTheType() {
        List<MedicalProviderDto> providers = Arrays.asList(
                new MedicalProviderDto(1L, "a", 1L, 1L, InstitutionType.PHYSICIAN,
                        "inst a", "dr. a", "boston", "ma", null, null, null, null),
                new MedicalProviderDto(2L, "b", 1L, 1L, InstitutionType.INITIAL_BIOPSY,
                        "inst b", "", "cambridge", "ma", null, null, null, null));

        Map<String, String> actual = fmt.collect(InstitutionType.INSTITUTION, providers);
        assertNotNull(actual);
        assertTrue(actual.isEmpty());

        actual = fmt.collect(InstitutionType.INITIAL_BIOPSY, providers);
        assertEquals(1, actual.size());
        assertEquals("inst b;cambridge;ma", actual.get("INITIAL_BIOPSY"));
    }

    @Test
    public void testCollect_physicianNameNotConsideredForOtherTypes() {
        Map<String, String> actual = fmt.collect(InstitutionType.INITIAL_BIOPSY, Arrays.asList(
                new MedicalProviderDto(1L, "a", 1L, 1L, InstitutionType.INITIAL_BIOPSY,
                        "inst a", "dr. a", "boston", "ma", null, null, null, null)));
        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("inst a;boston;ma", actual.get("INITIAL_BIOPSY"));

        actual = fmt.collect(InstitutionType.INSTITUTION, Arrays.asList(
                new MedicalProviderDto(1L, "a", 1L, 1L, InstitutionType.INSTITUTION,
                        "inst a", "dr. a", "boston", "ma", null, null, null, null)));
        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("inst a;boston;ma", actual.get("INSTITUTION"));
    }

    @Test
    public void testCollect_onlyConsidersRelevantFields() {
        Map<String, String> actual = fmt.collect(InstitutionType.PHYSICIAN, Arrays.asList(
                new MedicalProviderDto(1L, "a", 1L, 1L, InstitutionType.PHYSICIAN,
                        "inst a", "dr. a", "boston", "ma", "02115 - not used", "6171112233 - not used", "GUID - not used", null)));

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("dr. a;inst a;boston;ma", actual.get("PHYSICIAN"));
    }

    @Test
    public void testCollect_blankProviderIsSkipped() {
        Map<String, String> actual = fmt.collect(InstitutionType.PHYSICIAN, Arrays.asList(
                new MedicalProviderDto(1L, "a", 1L, 1L, InstitutionType.PHYSICIAN,
                        null, "", null, "", "02115", "6171112233", "GUID", "street1")));

        assertNotNull(actual);
        assertTrue(actual.isEmpty());
    }

    @Test
    public void testCollect_partialProvider() {
        Map<String, String> actual = fmt.collect(InstitutionType.PHYSICIAN, Arrays.asList(
                new MedicalProviderDto(1L, "a", 1L, 1L, InstitutionType.PHYSICIAN,
                        null, "dr. a", "boston", "", null, null, null, null)));

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("dr. a;;boston;", actual.get("PHYSICIAN"));
    }

    @Test
    public void testCollect_multipleProvidersPipeSeparated() {
        List<MedicalProviderDto> providers = Arrays.asList(
                new MedicalProviderDto(1L, "a", 1L, 1L, InstitutionType.PHYSICIAN,
                        "inst a", "dr. a", "boston", "ma", null, null, null, null),
                new MedicalProviderDto(2L, "b", 1L, 1L, InstitutionType.PHYSICIAN,
                        "inst b", "dr. b", "cambridge", "ma", null, null, null, null));

        Map<String, String> actual = fmt.collect(InstitutionType.PHYSICIAN, providers);

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("dr. a;inst a;boston;ma|dr. b;inst b;cambridge;ma", actual.get("PHYSICIAN"));
    }

    @Test
    public void testCollect_multipleProvidersMissingValues() {
        List<MedicalProviderDto> providers = Arrays.asList(
                new MedicalProviderDto(1L, "a", 1L, 1L, InstitutionType.PHYSICIAN,
                        "", "dr. a", "boston", null, null, null, null, null),
                new MedicalProviderDto(2L, "b", 1L, 1L, InstitutionType.PHYSICIAN,
                        "inst b", "", null, "ma", null, null, null, null));

        Map<String, String> actual = fmt.collect(InstitutionType.PHYSICIAN, providers);

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("dr. a;;boston;|;inst b;;ma", actual.get("PHYSICIAN"));
    }

    @Test
    public void testCollect_multipleProvidersInGivenOrder() {
        List<MedicalProviderDto> providers = Arrays.asList(
                new MedicalProviderDto(2L, "b", 1L, 1L, InstitutionType.INSTITUTION,
                        "inst b", "", "cambridge", "ma", null, null, null, null),
                new MedicalProviderDto(1L, "a", 1L, 1L, InstitutionType.INSTITUTION,
                        "inst a", "", "boston", "ma", null, null, null, null));

        Map<String, String> actual = fmt.collect(InstitutionType.INSTITUTION, providers);

        assertNotNull(actual);
        assertEquals(1, actual.size());
        assertEquals("inst b;cambridge;ma|inst a;boston;ma", actual.get("INSTITUTION"));
    }
}
