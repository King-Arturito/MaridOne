package org.example.maridone.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DefaultConfigTest {

    @Test
    void getApiUrlOrDefault_ShouldUseConfiguredApiUrl_WhenPresent() {
        DefaultConfig config = new DefaultConfig();
        config.setUrl("http://localhost:5173");
        config.setApiUrl("https://api.maridone.test/");

        Assertions.assertEquals("https://api.maridone.test", config.getApiUrlOrDefault());
    }

    @Test
    void getApiUrlOrDefault_ShouldMapViteDevPortToBackendPort() {
        DefaultConfig config = new DefaultConfig();
        config.setUrl("http://192.168.1.25:5173");

        Assertions.assertEquals("http://192.168.1.25:8080", config.getApiUrlOrDefault());
    }

    @Test
    void getApiUrlOrDefault_ShouldReuseSameOriginOutsideDevPorts() {
        DefaultConfig config = new DefaultConfig();
        config.setUrl("https://app.maridone.test");

        Assertions.assertEquals("https://app.maridone.test", config.getApiUrlOrDefault());
    }
}
