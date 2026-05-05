package com.example.insurancesystem.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class JsonUtil {

    @Autowired
    private ObjectMapper objectMapper;

    public Map<String, Object> parseJsonToMap(String jsonStr) {
        Map<String, Object> resultMap = null;
        try {
            // 2. 将JSON字符串转换为Map（TypeReference用于指定泛型类型）
            resultMap = objectMapper.readValue(
                    jsonStr,
                    new TypeReference<Map<String, Object>>() {}
            );
        } catch (Exception e) {
            // 捕获解析异常（比如JSON格式错误）
            e.printStackTrace();
        }

        return resultMap;
    }
}
