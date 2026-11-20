package com.example.insurancesystem.requestlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 验证溢出文件补投的删除边界，不依赖真实Redis或业务数据库。 */
class RequestLogTransportTest {
    @TempDir Path root;
    /** 发送失败保留完整文件，后续成功才删除，发送内容保持相同日志ID。 */
    @Test void replayRetainsFailureAndDeletesOnlyAfterRedisSuccess() throws Exception {
        RequestLogTransport transport=new RequestLogTransport(mock(RedisConnectionFactory.class),new ObjectMapper(),"test",1,root.toString());
        try {
            StringRedisTemplate redis=mock(StringRedisTemplate.class);
            StreamOperations<String,Object,Object> streams=mock(StreamOperations.class);
            when(redis.opsForStream()).thenReturn(streams);
            when(streams.add(eq(RequestLogTransport.STREAM),anyMap())).thenThrow(new IllegalStateException("offline")).thenReturn(RecordId.of("1-0"));
            ReflectionTestUtils.setField(transport,"redis",redis);
            Path file=root.resolve("test/one.ready");Files.writeString(file,"{\"logId\":\"same-id\"}");
            transport.replay();assertTrue(Files.exists(file));
            transport.replay();assertFalse(Files.exists(file));
            verify(streams,times(2)).add(eq(RequestLogTransport.STREAM),eq(Map.of("payload","{\"logId\":\"same-id\"}")));
        } finally { transport.close(); }
    }
}
