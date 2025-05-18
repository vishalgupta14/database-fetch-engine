package com.platform.engine.config;

import com.platform.engine.service.DatabaseFetchEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DslContextPreloader implements ApplicationRunner {

    private final DatabaseFetchEngine engine;

    @Override
    public void run(ApplicationArguments args) {
        engine.preloadContextsOnStartup();
    }
}
