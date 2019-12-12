package org.broadinstitute.ddp.model.activity.definition;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class ComponentBlockDefTest {

    private static Gson gson;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        gson = new GsonBuilder().serializeNulls()
                .registerTypeAdapter(FormBlockDef.class, new FormBlockDef.Deserializer())
                .registerTypeAdapter(ComponentBlockDef.class, new ComponentBlockDef.Deserializer())
                .create();
    }

    @Test
    public void testDeserialize_missingComponentType() {
        thrown.expect(JsonParseException.class);
        thrown.expectMessage(containsString("component type"));

        String json = "{\"blockType\":\"COMPONENT\"}";
        gson.fromJson(json, ComponentBlockDef.class);
    }

    @Test
    public void testDeserialize_addressComponentBlock() {
        String json = "{\"blockType\":\"COMPONENT\",\"componentType\":\"MAILING_ADDRESS\"}";

        FormBlockDef actual = gson.fromJson(json, FormBlockDef.class);
        assertNotNull(actual);
        assertEquals(BlockType.COMPONENT, actual.getBlockType());
        assertTrue(actual instanceof MailingAddressComponentDef);
    }

    @Test
    public void testDeserialize_physicianComponentBlock() {
        String json = "{\"blockType\":\"COMPONENT\",\"componentType\":\"PHYSICIAN\",\"allowMultiple\":true}";

        FormBlockDef actual = gson.fromJson(json, FormBlockDef.class);
        assertNotNull(actual);
        assertEquals(BlockType.COMPONENT, actual.getBlockType());
        assertTrue(((PhysicianComponentDef) actual).allowMultiple());
    }

    @Test
    public void testDeserialize_institutionComponentBlock() {
        String json = "{\"blockType\":\"COMPONENT\",\"componentType\":\"INSTITUTION\","
                + "\"allowMultiple\":false,\"institutionType\":\"INITIAL_BIOPSY\"}";

        FormBlockDef actual = gson.fromJson(json, FormBlockDef.class);
        assertNotNull(actual);
        assertEquals(BlockType.COMPONENT, actual.getBlockType());
        assertFalse(((InstitutionComponentDef) actual).allowMultiple());
        assertEquals(InstitutionType.INITIAL_BIOPSY, ((InstitutionComponentDef) actual).getInstitutionType());
    }
}
