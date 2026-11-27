package com.example.insurancesystem.ai;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 使用本地假模型服务验证三家供应商的日志开关，覆盖PDF、图片及整理路径，不发起外部请求。
 * 超过旧3000字符限制的文本用于验证完整输出，关闭时任何完成日志都不能携带正文。
 */
class PolicyOcrOutputLoggingTest {
    /**
     * 所有供应商共用开关语义：开启保留全文及尾部，关闭只记录摘要，不改变模型返回结果。
     */
    @Test void controlsFullOutputAcrossProvidersAndStages()throws Exception {
        String output="正文开始\n"+"识别内容".repeat(1000)+"\n正文末尾";
        ObjectMapper mapper=new ObjectMapper();String text=mapper.writeValueAsString(output);
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/chat/completions",exchange->{
            byte[] body=("{\"id\":\"test\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"test\",\"choices\":[{\"index\":0,\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":"+text+"}}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);try(var stream=exchange.getResponseBody()){stream.write(body);}
        });
        server.createContext("/responses",exchange->{
            byte[] body=("{\"output\":[{\"type\":\"message\",\"id\":\"msg_test\",\"role\":\"assistant\",\"status\":\"completed\",\"content\":[{\"type\":\"output_text\",\"text\":"+text+",\"annotations\":[]}]}]}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);try(var stream=exchange.getResponseBody()){stream.write(body);}
        });
        server.createContext("/interactions",exchange->{
            byte[] body=mapper.writeValueAsBytes(Map.of("steps",List.of(Map.of("type","model_output","content",List.of(Map.of("type","text","text",output))))));
            exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);try(var stream=exchange.getResponseBody()){stream.write(body);}
        });
        server.start();
        try {
            String base="http://127.0.0.1:"+server.getAddress().getPort();
            for(String provider:List.of("qwen","doubao","gemini"))for(boolean enabled:List.of(false,true)) {
                PolicyOcrModel client="gemini".equals(provider)
                    ?new GeminiPolicyOcrClient("test-key",base,"ocr","organizer",mapper,enabled)
                    :new PolicyOcrModelClient(provider,"test-key",base,"ocr","organizer",enabled);
                Logger logger=(Logger)LoggerFactory.getLogger(client.getClass());boolean additive=logger.isAdditive();Level level=logger.getLevel();
                ListAppender<ILoggingEvent> appender=new ListAppender<>();appender.start();logger.setAdditive(false);logger.setLevel(Level.INFO);logger.addAppender(appender);
                try {
                    assertEquals(output,client.recognize("https://example.test/policy.pdf",true));
                    assertEquals(output,client.recognize("https://example.test/policy.png",false));
                    assertEquals(output,client.organize("{}",List.of("测试校验问题")));
                    var completed=appender.list.stream().map(ILoggingEvent::getFormattedMessage).filter(message->message.startsWith("大模型调用完成")).toList();
                    assertEquals(3,completed.size());
                    for(String message:completed) {
                        assertEquals(enabled,message.contains(output),provider);
                        assertEquals(enabled,message.contains("正文末尾"),provider);
                        assertTrue(message.contains("outputChars="+output.length()));
                    }
                    assertTrue(completed.stream().anyMatch(message->message.contains("stage=结构整理")));
                }finally{logger.detachAppender(appender);logger.setAdditive(additive);logger.setLevel(level);appender.stop();}
            }
        }finally{server.stop(0);}
    }
}
