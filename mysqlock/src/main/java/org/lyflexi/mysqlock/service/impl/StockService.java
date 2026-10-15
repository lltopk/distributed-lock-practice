package org.lyflexi.mysqlock.service.impl;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import lombok.extern.slf4j.Slf4j;
import org.lyflexi.entity.Stock;
import org.lyflexi.mysqlock.mapper.StockMapper;
import org.lyflexi.mysqlock.service.IStockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class StockService extends ServiceImpl<StockMapper, Stock> implements IStockService {
    @Transactional
    @Override
    public void deductOneSql(Long productId) {
        this.baseMapper.updateStockOneSql(productId, 10);
    }


    @Override
    @Transactional
    public void deductForUpdate(Long productId) {
        //先加for update锁
        Stock stock = this.baseMapper.selectStockForUpdate(productId);
        checkBeforeDeduct(stock);
        try {
            Thread.sleep(10000l);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        //count = count -1 不写在sql内，模拟线程不安全的操作，看前面的for update 是否生效
        stock.setCount(stock.getCount()-1);
        this.baseMapper.updateStock(stock);
        //事务提交后才释放for update锁
    }


    @Override
    public void tryDeductByRecursive(Long productId, Integer retry) {
        try {
            Stock stock = this.getOne(Wrappers.<Stock>lambdaQuery().eq(Stock::getProductId, productId));
            checkBeforeDeduct(stock);
            stock.setCount(stock.getCount()-1);
            //更新成功
            if (this.baseMapper.updateStockOptimistic(stock) > 0){
                log.info("乐观锁更新成功：当前商品：{}",productId);
            }
            if(retry>0){
                retry--;
                tryDeductByRecursive(productId, retry);
                log.info("乐观锁重试：当前商品：{}",productId);
            }

            //retry == 0 递归结束
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public void tryDeductBySpinning(Long productId, Integer retry) {
        Stock stock = null;
        try {
            stock = this.getOne(Wrappers.<Stock>lambdaQuery().eq(Stock::getProductId, productId));
            checkBeforeDeduct(stock);
            do{
                stock.setCount(stock.getCount()-1);
                int flag = this.baseMapper.updateStockOptimistic(stock);
                //cas成功
                if (flag ==1 ) {
                    log.info("乐观锁更新成功：当前商品：{}",productId);
                    break;
                }
                retry--;
                //再次查出来, 确保新一轮的重试
                stock = this.getOne(Wrappers.<Stock>lambdaQuery().eq(Stock::getProductId, productId));
                checkBeforeDeduct(stock);
                log.info("乐观锁自旋重试：当前商品：{}",productId);
            }while (retry > 0);

            //retry == 0 递归结束
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void checkBeforeDeduct(Stock stock) {
        if (stock == null){
            throw new RuntimeException("stock not exist");
        }
    }
}
