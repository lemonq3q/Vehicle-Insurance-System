package com.example.insurancesystem.saas.support;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PortalMapsTest {
  @Test
  void convertsDatabaseColumnsAndKeepsExistingCamelCase() {
    Map<String, Object> source = new LinkedHashMap<>();
    source.put("enterprise_id", 20L);
    source.put("roleCode", "OWNER");
    Map<String, Object> result = PortalMaps.camel(source);
    assertEquals(20L, result.get("enterpriseId"));
    assertEquals("OWNER", result.get("roleCode"));
  }
}
