package com.example.insurancesystem.coordinator.client;

import com.example.insurancesystem.coordinator.config.CoordinatorProperties;
import com.example.insurancesystem.maintenance.MaintenanceProtocol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证内部维护请求只重试无响应类故障，并在达到阈值后把控制权交还协调器。 */
class ParticipantClientTest {
    private RestTemplate restTemplate;
    private CoordinatorProperties properties;
    private CoordinatorProperties.ServiceEndpoint endpoint;
    private ParticipantClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        properties = new CoordinatorProperties();
        properties.setInternalSecret("test-secret");
        properties.setRequestMaxAttempts(3);
        properties.setRequestRetryDelayMs(0);
        endpoint = new CoordinatorProperties.ServiceEndpoint();
        endpoint.setServiceId("saas-backend");
        endpoint.setBaseUrl("http://localhost:8081");
        client = new ParticipantClient(restTemplate, properties);
    }

    /** 前两次连接失败、第三次正常返回时，应返回成功响应且总共发送三次相同请求。 */
    @Test
    void retriesNetworkFailureUntilResponseArrives() {
        MaintenanceProtocol.Response expected = MaintenanceProtocol.Response.of(true, "ready", null);
        when(exchange()).thenThrow(new ResourceAccessException("timeout"))
                .thenThrow(new ResourceAccessException("connection reset"))
                .thenReturn(ResponseEntity.ok(expected));

        MaintenanceProtocol.Response actual = client.start(endpoint, new MaintenanceProtocol.StartRequest());

        assertSame(expected, actual);
        verify(restTemplate, times(3)).exchange(anyString(), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(MaintenanceProtocol.Response.class));
    }

    /** 连续无响应达到配置阈值后返回 null，防止客户端无限阻塞协调流程。 */
    @Test
    void returnsNullAfterRetryThreshold() {
        when(exchange()).thenThrow(new ResourceAccessException("timeout"));

        assertNull(client.heartbeat(endpoint, new MaintenanceProtocol.HeartbeatRequest()));
        verify(restTemplate, times(3)).exchange(anyString(), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(MaintenanceProtocol.Response.class));
    }

    /** 4xx 是接收端明确拒绝而不是无响应，不应通过重试重复发送无效或未授权请求。 */
    @Test
    void doesNotRetryClientError() {
        when(exchange()).thenThrow(HttpClientErrorException.create(
                HttpStatus.FORBIDDEN, "forbidden", HttpHeaders.EMPTY, new byte[0], null));

        assertThrows(HttpClientErrorException.class,
                () -> client.finish(endpoint, new MaintenanceProtocol.FinishRequest()));
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(MaintenanceProtocol.Response.class));
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<MaintenanceProtocol.Response> exchange() {
        return restTemplate.exchange(anyString(), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(MaintenanceProtocol.Response.class));
    }
}
