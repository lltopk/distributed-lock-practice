package org.lyflexi.mysqlock.controller;

import org.lyflexi.mysqlock.service.impl.StockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mock")
public class MockController {

    @Autowired
    private StockService stockService;

    @GetMapping("/stock/deduct")
    public String deduct(@RequestParam("productId") Long productId){
        stockService.deductOneSql(productId);
        return "hello stock deduct！！";
    }

    @GetMapping("/stock/deductForUpdate")
    public String deductForUpdate(@RequestParam("productId") Long productId){
        stockService.deductForUpdate(productId);
        return "hello stock deduct！！";
    }


    @GetMapping("/stock/deductOptimistic")
    public String deductOptimistic(@RequestParam("productId") Long productId){
        stockService.deductOptimistic(productId);
        return "hello stock deduct！！";
    }

    @GetMapping("/stock/optimisticRetryByRecursive")
    public String optimisticRetryByRecursive(@RequestParam("productId") Long productId){
        stockService.optimisticRetryByRecursive(productId);
        return "hello stock deduct！！";
    }

    @GetMapping("/stock/optimisticRetryBySpinning")
    public String optimisticRetryBySpinning(@RequestParam("productId") Long productId){
        stockService.optimisticRetryBySpinning(productId);
        return "hello stock deduct！！";
    }

}

