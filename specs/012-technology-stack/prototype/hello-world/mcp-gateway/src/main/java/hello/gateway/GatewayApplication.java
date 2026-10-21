package hello.gateway;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Stateless MCP gateway (AD-21): MCP Java SDK 2.1.0's stateful Streamable HTTP servlet transport, hosted in
 * Spring Boot's embedded server. It keeps no state of its own and forwards the caller's driver token to the
 * engine's HTTP driver API unchanged.
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }

    @Bean
    RestClient engine(@Value("${gateway.engine-url}") String engineUrl) {
        return RestClient.builder().baseUrl(engineUrl).build();
    }

    @Bean
    HttpServletStreamableServerTransportProvider transport() {
        return HttpServletStreamableServerTransportProvider.builder()
                .jsonMapper(McpJsonDefaults.getMapper())
                .mcpEndpoint("/mcp")
                .contextExtractor(req -> McpTransportContext.create(
                        Map.of("authorization", String.valueOf(req.getHeader("Authorization")))))
                .build();
    }

    @Bean
    ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServlet(
            HttpServletStreamableServerTransportProvider transport) {
        var bean = new ServletRegistrationBean<>(transport, "/mcp", "/mcp/*");
        bean.setAsyncSupported(true);
        return bean;
    }

    @Bean(destroyMethod = "close")
    McpSyncServer mcpServer(HttpServletStreamableServerTransportProvider transport, RestClient engine) {
        var status = SyncToolSpecification.builder()
                .tool(Tool.builder("status").description("The run for a subject. Call this first.")
                        .inputSchema(Map.of("type", "object",
                                "properties", Map.of("subject", Map.of("type", "string",
                                        "description", "The subject key.")),
                                "required", List.of("subject")))
                        .build())
                .callHandler((ex, req) -> {
                    var token = (String) ex.transportContext().get("authorization");
                    try {
                        var body = engine.get()
                                .uri(u -> u.path("/v1/status").queryParam("subject", req.arguments().get("subject")).build())
                                .header("Authorization", token).retrieve().body(String.class);
                        return CallToolResult.builder().addTextContent(body).build();
                    } catch (ResourceAccessException e) {
                        return error("engine unreachable: fail closed");
                    } catch (HttpClientErrorException e) {
                        return error("engine answered " + e.getStatusCode().value());
                    }
                })
                .build();
        return McpServer.sync(transport)
                .serverInfo("mcp-gateway", "0")
                .capabilities(ServerCapabilities.builder().tools(false).build())
                .tools(status)
                .build();
    }

    /** Checks the token with the engine on every MCP request, so a bad token is a 401 at connect time. */
    @Bean
    FilterRegistrationBean<Filter> tokenCheck(RestClient engine) {
        Filter filter = (request, response, chain) -> {
            var req = (HttpServletRequest) request;
            var res = (HttpServletResponse) response;
            var auth = req.getHeader("Authorization");
            try {
                engine.get().uri("/v1/whoami").header("Authorization", auth == null ? "" : auth)
                        .retrieve().toBodilessEntity();
            } catch (HttpClientErrorException e) {
                res.sendError(e.getStatusCode().value(), "driver token rejected by the engine");
                return;
            } catch (ResourceAccessException e) {
                res.sendError(503, "engine unreachable");
                return;
            }
            chain.doFilter(request, response);
        };
        var bean = new FilterRegistrationBean<>(filter);
        bean.addUrlPatterns("/mcp", "/mcp/*");
        return bean;
    }

    static CallToolResult error(String message) {
        return CallToolResult.builder().addTextContent(message).isError(true).build();
    }
}
