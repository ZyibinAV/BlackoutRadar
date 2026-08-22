package com.zyibin.app.blackoutradar;

import org.junit.jupiter.api.Test;
import com.zyibin.app.blackoutradar.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class BlackoutRadarApplicationTests {

    @Test
    void contextLoads() {
    }

}
