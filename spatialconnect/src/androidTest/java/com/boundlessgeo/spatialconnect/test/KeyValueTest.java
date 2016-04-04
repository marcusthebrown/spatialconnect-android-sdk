package com.boundlessgeo.spatialconnect.test;

import com.boundlessgeo.spatialconnect.db.KeyValueDAO;

import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static junit.framework.Assert.assertEquals;

public class KeyValueTest extends BaseTestCase {


    @Before
    public void resetDb() throws Exception {
        testContext.deleteDatabase(KeyValueDAO.DATABASE_NAME);
    }

    @Test
    public void testPutIntoKeyValueDB() {
        KeyValueDAO keyValueDAO = new KeyValueDAO(testContext);
        keyValueDAO.put("stores.1234.type", "gpkg");
        Map<String, Object> value = keyValueDAO.getValuesForKeyPrefix("stores");
        assertEquals("There should be 1 value returned.", 1, value.size());
    }

    @Test
    public void testGetValueForKey() {
        KeyValueDAO keyValueDAO = new KeyValueDAO(testContext);
        keyValueDAO.put("stores.1234.type", "gpkg");
        Map<String, Object> value = keyValueDAO.getValueForKey("stores.1234.type");
        assertEquals("There should be 1 value returned for the key.", "gpkg", value.get("stores.1234.type"));
    }

    @Test
    public void testGetValuesForKeyPrefix() {
        KeyValueDAO keyValueDAO = new KeyValueDAO(testContext);
        keyValueDAO.put("stores.1234.type", "gpkg");
        keyValueDAO.put("stores.1234.isMainBundle", false);
        Map<String, Object> values = keyValueDAO.getValuesForKeyPrefix("stores");
        assertEquals("There should be 2 values returned.", 2, values.size());
    }

    @Test
    public void testBooleanDeserialize() {
        KeyValueDAO keyValueDAO = new KeyValueDAO(testContext);
        keyValueDAO.put("stores.1234.isMainBundle", Boolean.FALSE);
        Map<String, Object> stores = keyValueDAO.getValuesForKeyPrefix("stores.1234.isMainBundle");
        assertEquals("The value should be deserialized to a Boolean.",
                Boolean.FALSE, stores.get("stores.1234.isMainBundle")
        );
    }

    @Test
         public void testStringDeserialize() {
        KeyValueDAO keyValueDAO = new KeyValueDAO(testContext);
        keyValueDAO.put("stores.1234.type", "geojson");
        Map<String, Object> stores = keyValueDAO.getValuesForKeyPrefix("stores.1234.type");
        assertEquals("The value should be deserialized to a String.",
                "geojson", stores.get("stores.1234.type")
        );
    }

    @Test
    public void testLongDeserialize() {
        KeyValueDAO keyValueDAO = new KeyValueDAO(testContext);
        keyValueDAO.put("stores.1234.longNumber", Long.valueOf("100"));
        Map<String, Object> stores = keyValueDAO.getValuesForKeyPrefix("stores.1234.longNumber");
        assertEquals("The value should be deserialized to a Integer.",
                Integer.valueOf("100").getClass(), stores.get("stores.1234.longNumber").getClass()
        );
    }

    @Test
    public void testIntegerDeserialize() {
        KeyValueDAO keyValueDAO = new KeyValueDAO(testContext);
        keyValueDAO.put("stores.1234.integer", Integer.valueOf("100"));
        Map<String, Object> stores = keyValueDAO.getValuesForKeyPrefix("stores.1234.integer");
        assertEquals("The value should be deserialized to a Long.",
                Integer.valueOf("100"), stores.get("stores.1234.integer")
        );
    }

    @Test
    public void testDoubleDeserialize() {
        KeyValueDAO keyValueDAO = new KeyValueDAO(testContext);
        keyValueDAO.put("stores.1234.double", Double.valueOf("100.001"));
        Map<String, Object> stores = keyValueDAO.getValuesForKeyPrefix("stores.1234.double");
        assertEquals("The value should be deserialized to a Double.",
                Double.valueOf("100.001"), stores.get("stores.1234.double")
        );
    }

    @Test
    public void testFloatDeserialize() {
        KeyValueDAO keyValueDAO = new KeyValueDAO(testContext);
        keyValueDAO.put("stores.1234.float", Float.valueOf("100.001"));
        Map<String, Object> stores = keyValueDAO.getValuesForKeyPrefix("stores.1234.float");
        assertEquals("The value should be deserialized to a Double.",
                Double.valueOf("100.001"), stores.get("stores.1234.float")
        );
    }

}
