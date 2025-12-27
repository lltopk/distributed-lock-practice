package org.lyflexi.mysqlock.service.impl;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import lombok.extern.slf4j.Slf4j;
import org.lyflexi.entity.Stock;
import org.lyflexi.mysqlock.mapper.StockMapper;
import org.lyflexi.mysqlock.service.IStockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
public class StockService extends ServiceImpl<StockMapper, Stock> implements IStockService {
    @Transactional
    @Override
    public void deductOneSql(Long productId) {
        Stock stock = null;
        try {
            stock = this.getOne(Wrappers.<Stock>lambdaQuery()
                    .eq(Stock::getProductId, productId));
        } catch (Exception e) {
            throw new RuntimeException("getOne库存异常");
        }
        if (stock != null) {
            this.baseMapper.updateStockOneSql(stock);
        }
    }


    @Override
    @Transactional
    public void deductForUpdate(Long productId) {
        //先加for update锁
        Stock stock = this.baseMapper.selectStockForUpdate(productId);
        try {
            Thread.sleep(10000l);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (stock != null) {
            //count = count -1 不写在sql内，模拟线程不安全的操作，看前面的for update 是否生效
            stock.setCount(stock.getCount()-1);
            this.baseMapper.updateStock(stock);
        }
    }


    @Override
    public void optimisticRetryByRecursive(Long productId,Integer retry) {
        Stock stock = null;
        try {
            stock = this.getOne(Wrappers.<Stock>lambdaQuery()
                    .eq(Stock::getProductId, productId));
        } catch (Exception e) {
            throw new RuntimeException("getOne库存异常");
        }
        if (stock != null) {
            stock.setCount(stock.getCount()-1);
            //返回0表示cas失败
            if (this.baseMapper.updateStockOptimistic(stock) == 0){
                if(retry>0){
                    retry--;
                    optimisticRetryByRecursive(productId,retry);
                    log.info("乐观锁重试：当前商品：{}",productId);
                }
            }
            log.info("乐观锁更新成功：当前商品：{}",productId);
            return;
        }
    }




    @Override
    public void optimisticRetryBySpinning(Long productId,Integer retry) {
        Stock stock = null;
        try {
            stock = this.getOne(Wrappers.<Stock>lambdaQuery()
                    .eq(Stock::getProductId, productId));
        } catch (Exception e) {
            throw new RuntimeException("getOne库存异常");
        }

        if (stock == null) {
            return;
        }
        stock.setCount(stock.getCount()-1);
        do{
            //返回0表示cas失败
            int flag = this.baseMapper.updateStockOptimistic(stock);
            //cas成功
            if (flag ==1 ) {
                break;
            }
            retry--;
            //再次查出来, 确保新一轮的重试
            stock = this.getOne(Wrappers.<Stock>lambdaQuery()
                    .eq(Stock::getProductId, productId));
            log.info("乐观锁自旋重试：当前商品：{}",productId);
        }while (retry > 0);
    }

    @Override
    public void modifyName(Long productId, String modifyName) {
        this.baseMapper.modifyName(productId,modifyName);
    }

}
