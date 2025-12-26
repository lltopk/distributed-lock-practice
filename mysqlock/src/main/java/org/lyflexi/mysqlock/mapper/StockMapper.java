package org.lyflexi.mysqlock.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.lyflexi.entity.Stock;

/**
 * @Author: ly
 * @Date: 2024/3/25 21:36
 */
@Mapper
public interface StockMapper extends BaseMapper<Stock> {

    /**
     * count = count -1，mysql会自动加行锁
     * @param stock
     */
    public void updateStockOneSql(@Param("stock") Stock stock) ;

    /**
     * for update 悲观锁
     * @param productId
     * @return
     */
    public Stock selectStockForUpdate(@Param("productId") Long productId) ;

    /**
     * 非count = count -1,因此线程不安全
     * 结合selectStockForUpdate使用
     * @param stock
     */
    public void updateStock(@Param("stock") Stock stock) ;


    /**
     * 乐观锁：版本号机制
     * @param stock
     * @return
     */
    public int updateStockOptimistic(@Param("stock") Stock stock) ;

    void modifyName(@Param("productId")Long productId, @Param("modifyName") String modifyName);
}



