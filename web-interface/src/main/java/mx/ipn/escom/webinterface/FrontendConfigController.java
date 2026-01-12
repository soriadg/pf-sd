package mx.ipn.escom.webinterface;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class FrontendConfigController {

    private final ObjectMapper objectMapper;

    @Value("${app.account-base-url:http://localhost:8080}")
    private String accountBaseUrl;

    @Value("${app.auth-base-url:http://localhost:8081}")
    private String authBaseUrl;

    @Value("${app.report-base-url:http://localhost:8084}")
    private String reportBaseUrl;

    public FrontendConfigController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @GetMapping(value = "/config.js", produces = "application/javascript")
    public String configJs() {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("ACCOUNT_BASE_URL", accountBaseUrl);
        config.put("AUTH_BASE_URL", authBaseUrl);
        config.put("REPORT_BASE_URL", reportBaseUrl);
        try {
            return "window.__CONFIG__ = " + objectMapper.writeValueAsString(config) + ";";
        } catch (JsonProcessingException e) {
            return "window.__CONFIG__ = {};";
        }
    }
}
