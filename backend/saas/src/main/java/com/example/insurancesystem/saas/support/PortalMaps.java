package com.example.insurancesystem.saas.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class PortalMaps {
  private PortalMaps() {}

  public static Map<String, Object> camel(Map<String, Object> source) {
    if (source == null) return null;
    Map<String, Object> result = new LinkedHashMap<>();
    source.forEach((key, value) -> result.put(toCamel(key), value));
    /* 微信绑定是接口布尔状态，MySQL CASE 可能返回数值，统一规范避免前端误判。 */
    if (result.containsKey("wechatBound")) {
      Object bound = result.get("wechatBound");
      result.put("wechatBound", Boolean.TRUE.equals(bound) || (bound instanceof Number && ((Number) bound).intValue() == 1));
    }
    return result;
  }

  public static List<Map<String, Object>> camel(List<Map<String, Object>> source) {
    return source.stream().map(PortalMaps::camel).collect(Collectors.toList());
  }

  public static Object get(Map<String, Object> map, String camel, String snake) {
    if (map == null) return null;
    return map.containsKey(camel) ? map.get(camel) : map.get(snake);
  }

  public static Long longValue(Map<String, Object> map, String camel, String snake) {
    Object value = get(map, camel, snake);
    return value == null ? null : ((Number) value).longValue();
  }

  public static Integer intValue(Map<String, Object> map, String camel, String snake) {
    Object value = get(map, camel, snake);
    return value == null ? null : ((Number) value).intValue();
  }

  private static String toCamel(String value) {
    if (!value.contains("_")) {
      return value.equals(value.toUpperCase()) ? value.toLowerCase() : value;
    }
    StringBuilder result = new StringBuilder();
    boolean upper = false;
    for (char c : value.toLowerCase().toCharArray()) {
      if (c == '_') upper = true;
      else {
        result.append(upper ? Character.toUpperCase(c) : c);
        upper = false;
      }
    }
    return result.toString();
  }
}
