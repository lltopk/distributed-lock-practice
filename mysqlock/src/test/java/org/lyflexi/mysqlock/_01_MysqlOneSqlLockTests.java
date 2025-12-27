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
class _01_MysqlOneSqlLockTests {

    @Autowired
    IStockService stockService;

    /**
     * CompletableFuture[]
     */
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

    /**
     * List to CompletableFuture[]
     */
    @Test
    void contextLoads2() {
        List<CompletableFuture> futures = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                stockService.deductOneSql(1l);
            });
            futures.add(future);
        }
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        allOf.join();

        log.info("end");
    }

    /**
     * Stream to CompletableFuture[]
     */
    @Test
    void contextLoads3() {
        List<CompletableFuture> futures = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                stockService.deductOneSql(1l);
            });
            futures.add(future);
        }
        CompletableFuture<Void> allOf = CompletableFuture.allOf(futures.stream().toArray(CompletableFuture[]::new));
        allOf.join();

        log.info("end");
    }

}
