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

        //future blocked by future
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            long start = System.currentTimeMillis();
            log.info("before modify");
            stockService.modifyName(1l, "modifyName");
            log.info("after modify consuming time {}", (System.currentTimeMillis() - start) / 1000);
        });

        /**
         * JUnit框架设计的初衷是为了快速、独立地运行测试用例。每个测试方法执行时，JUnit会启动一个新的线程来处理，并在测试结束后主动终止该线程。
         *
         * 具体控制流程为：
         * 创建新的线程执行每个测试方法。
         * 测试方法执行完毕后，无论非守护线程（如存在）是否完成，JUnit都会结束测试流程及其相关线程。
         * 这样，即使测试方法内存在未完成的非守护线程，它们也不会阻止JUnit结束。
         */
        future.join();

        log.info("end");
    }

}
