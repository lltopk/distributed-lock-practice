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
    @GetMapping("/stock/deductForUpdate/concurentModify")
    public String concurentModify(@RequestParam("productId") Long productId){
        stockService.modifyName(productId,"modifyName");
        return "modifyName";
    }

    private static final Integer retry = 10;
    @GetMapping("/stock/optimisticRetryByRecursive")
    public String deductOptimistic(@RequestParam("productId") Long productId){
        stockService.optimisticRetryByRecursive(productId,retry);
        return "hello stock deduct！！";
    }


    @GetMapping("/stock/optimisticRetryBySpinning")
    public String optimisticRetryBySpinning(@RequestParam("productId") Long productId){
        stockService.optimisticRetryBySpinning(productId,retry);
        return "hello stock deduct！！";
    }

}

