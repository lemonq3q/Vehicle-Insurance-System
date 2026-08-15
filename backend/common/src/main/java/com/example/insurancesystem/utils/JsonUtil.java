package com.example.insurancesystem.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
/**
 * 复用项目主 ObjectMapper 在通用 Map、领域对象与 JSON 文本之间转换，主要服务于外部 SDK 数据适配。
 */
public class JsonUtil {

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 将 JSON 对象文本解析为字符串键的通用 Map；格式非法时记录异常并返回 null，由调用方按外部服务失败处理。
     */
    public Map<String, Object> parseJsonToMap(String jsonStr) {
        Map<String, Object> resultMap = null;
        try {
            resultMap = objectMapper.readValue(
                    jsonStr,
                    new TypeReference<Map<String, Object>>() {}
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        return resultMap;
    }

    /**
     * 使用缩进格式将任意对象序列化为 JSON，便于 OCR 原始结果读取和日志检查；无法序列化时返回 null。
     */
    public String parseObjectToJson(Object object){
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return null;
        }
    }
}
