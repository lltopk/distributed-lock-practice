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
class _03_MysqlOptimisticLockTests {

    @Autowired
    IStockService stockService;

    /**
     * CompletableFuture[]
     *
     * 最终库存从10000->0
     * version从0->10000
     */
    @Test
    void contextLoads() {
        CompletableFuture[] completableFutures = new CompletableFuture[10000];
        for (int i = 0; i < 10000; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                //重试1000次, 保证都成功
                //实际生产不可这样 , 递归容易OOM
                stockService.optimisticRetryByRecursive(1l,1000);
            });
            completableFutures[i] = future;
        }
        CompletableFuture<Void> allOf = CompletableFuture.allOf(completableFutures);
        allOf.join();

        log.info("end");
    }

    /**
     * CompletableFuture[]
     *
     * 最终库存从10000->0
     * version从0->10000
     */
    @Test
    void contextLoads2() {
        CompletableFuture[] completableFutures = new CompletableFuture[10000];
        for (int i = 0; i < 10000; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                //重试1000次, 保证都成功
                //实际生产不可这样 , 自旋容易CPU UP UP
                stockService.optimisticRetryBySpinning(1l,1000);
            });
            completableFutures[i] = future;
        }
        CompletableFuture<Void> allOf = CompletableFuture.allOf(completableFutures);
        allOf.join();

        log.info("end");
    }

}
