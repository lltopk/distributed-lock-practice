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
        if (stock != null) {
            //count = count -1 不写在sql内，模拟线程不安全的操作，看前面的for update 是否生效
            stock.setCount(stock.getCount()-1);
            this.baseMapper.updateStock(stock);
        }
    }

    /**
     * 注意这里不能加事务，否则乐观锁将会失效，cpu会飙高
     * @param productId
     */
    @Override
    public void deductOptimistic(Long productId) {

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
                log.info("乐观锁重试：当前商品：{}",productId);
                deductOptimistic(productId);
            }
        }
    }

    private static final Integer retry = 10;
    @Override
    public void optimisticRetryByRecursive(Long productId) {
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
        doRetryByRecursive(stock,retry);
    }

    @Override
    public void optimisticRetryBySpinning(Long productId) {
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
        doRetryBySpinning(stock,retry);
    }

    private void doRetryBySpinning(Stock stock, Integer retry) {
        stock.setCount(stock.getCount()-1);
        Long productId = stock.getProductId();
        do{
            //返回0表示cas失败
            int flag = this.baseMapper.updateStockOptimistic(stock);
            //cas成功
            if (flag ==1 ) {
                break;
            }
            retry--;
            log.info("乐观锁自旋重试：当前商品：{}",productId);
        }while (retry > 0);
    }

    private void doRetryByRecursive(Stock stock, int retry) {
        stock.setCount(stock.getCount()-1);
        Long productId = stock.getProductId();
        //返回0表示cas失败
        int flag = this.baseMapper.updateStockOptimistic(stock);
        //cas成功
        if (flag ==1 ) {
            return;
        }
        //cas失败重试
        if (retry > 0) {
            retry--;
            log.info("乐观锁递归重试：当前商品：{}",productId);
            doRetryByRecursive(stock,retry);
        }

    }

}
