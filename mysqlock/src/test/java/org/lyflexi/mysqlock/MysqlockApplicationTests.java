package org.lyflexi.mysqlock;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.lyflexi.mysqlock.service.IStockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CompletableFuture;

@SpringBootTest
@Slf4j
class MysqlockApplicationTests {

    @Autowired
    IStockService stockService;
    @Test
    void contextLoads() {
        CompletableFuture[] completableFutures = new CompletableFuture[10000];
        for (int i = 0; i < 10000; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                stockService.deductOneSql(1l);
            });
            completableFutures[i] = future;
        }
        CompletableFuture<Void> allOf = CompletableFuture.allOf(completableFutures);
        allOf.join();

        log.info("end");
    }

}
