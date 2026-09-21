package com.zyibin.app.blackoutradar.infrastructure.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JwtPropertiesContextTest {

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void shouldBindJwtProperties() {
        assertThat(jwtProperties.getIssuer()).isEqualTo("blackoutradar");
        assertThat(jwtProperties.getAudience()).isEqualTo("blackoutradar-api");
        assertThat(jwtProperties.getAccessTokenLifetime()).isNotNull();
    }
}
