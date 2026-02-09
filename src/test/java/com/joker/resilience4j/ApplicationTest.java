package com.joker.resilience4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

class ApplicationTest {

    @Test
    void shouldStartAndStopApplication() {
        ConfigurableApplicationContext context = Application.run("--spring.main.web-application-type=none");
        assertThat(context.isActive()).isTrue();
        Application.stop();
        assertThat(context.isActive()).isFalse();

        Application.main(new String[]{"--spring.main.web-application-type=none"});
        Application.stop();

        // Call stop again when context is already null
        Application.stop();
    }
}
