package com.example.insurancesystem.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.insurancesystem.domain.encapsulate.ResponseResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SaasSsoResponseJsonTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldDeserializeSaasSsoResponse() throws Exception {
        String json = "{\"code\":200,\"msg\":\"授权码兑换成功\","
                + "\"data\":{\"userId\":10001,\"enterpriseId\":20001}}";

        ResponseResult<?> response = new ObjectMapper().readValue(json, ResponseResult.class);

        assertEquals(200, response.getCode());
        assertEquals("授权码兑换成功", response.getMsg());
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertEquals(10001, ((Number) data.get("userId")).intValue());
        assertEquals(20001, ((Number) data.get("enterpriseId")).intValue());
    }
}
