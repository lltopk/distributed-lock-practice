package org.lyflexi.mysqlock;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.lyflexi.mysqlock.service.IStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@SpringBootTest
@Slf4j
class _02_MysqlForUpdateTests {

    @Autowired
    IStockService stockService;

    @Test
    void contextLoads() {
        CompletableFuture.runAsync(() -> {
            stockService.deductForUpdate(1l);
        });

        log.info("end");
    }

}
