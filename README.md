## mysql分布式锁
MySQL自身有3种分布式锁方案：

单元测试见: [mysqlock](mysqlock/src/test/java/org/lyflexi/mysqlock)

### 1.1 update瞬时行锁
InnoDB存储引擎在执行记录行更新的时候会自动添加排他锁。利用这一特性，我们可以解决超卖问题，具体SQL如下：

```sql
update stock set count = count - 1 where product_id = 1 and count > 0;
```
即使MySQL会自动添加行排他锁, 但推荐使用上述形如 `count = count - 1` 的写法, 原因如下:

如果两个请求都先读取库存 10，然后在Java代码层面各自计算出 newcount = 9，再执行固定值更新，最终库存可能仍然是 9，而不是 8。

## 1.2 select...for update行锁
for update显式加排他锁 + 普通的update语句

虽然mysql update是自动加锁的且保证原子的, 但它是瞬时行为, 类似上述的问题, 无法保证这一条sql前后代码整体的原子性
```txt
T1                       T2
select = 10              select = 10
update = 10 - 1 = 9
commit

                         update = 10 - 1 = 9
                         commit
```

要想持有锁直到commit之间保证这一期间的线程安全, 需要先在查询的时候加上`for update`排他锁，然后执行具体的更新业务逻辑, 比如xxljob
```sql
SELECT lock_name FROM xxl_job_lock WHERE lock_name = 'schedule_lock' FOR UPDATE;
```

xxljob的逻辑是, 需要保持锁持有状态, 并在这期间执行具体的业务操作, 防止定时任务重复触发
```sql
拿锁
↓
扫描任务
↓
触发任务
↓
释放锁
```

回到本例, 参考xxljob应该如是说
```sql
---第一个线程
<select id="selectStockForUpdate" resultType="org.lyflexi.entity.Stock">
    select * from stock where product_id = #{productId} for update
</select>
<update id="updateStock">
    update stock set count = #{stock.count} where product_id = #{stock.productId}
</update>

---第二个线程, 执行for update无法重复获取锁, 因此被阻塞在这里, 直到第一个线程完成更新后释放锁
<select id="selectStockForUpdate" resultType="org.lyflexi.entity.Stock">
    select * from stock where product_id = #{productId} for update
</select>
<update id="updateStock">
    update stock set count = #{stock.count} where product_id = #{stock.productId}
</update>
```

for update语句必须在事务中才能有效, 要么@Transactional, 要么手动管理事务,

否则如果 Spring 管理的数据库连接启用了自动提交（通常如此），那么单条 SELECT ... FOR UPDATE 执行结束时，其隐式事务就可能已经提交，锁也随之释放。这样，后面的 UPDATE 就无法受到for update的保护了。
```java
//反例, 没有事务管理
public void deductStock() {
    Stock stock = stockMapper.selectForUpdate(1);
    // 其他业务操作
    stockMapper.decreaseStock(1);
}
```

虽说for update性能不好, 但 XXL-JOB 调度端的真实并发是：

**调度中心来调度任务执行, 而调度中心的实例数 1～3（极少超过 5）**, 所以for update并不会影响xxljob的性能瓶颈, 一个反直觉的事实是阻塞≠慢

其实调度系统最怕的三件事
- 并发调度	任务被执行多次
- 调度丢失	任务没执行
- 主从切换异常	调度混乱

这些 比慢 10ms 严重得多. 而且不引入redis等分布式锁也避免额外维护中间件

### 1.3 乐观策略version
for update是悲观策略, 并发线程无法在第一个for update提交之前再次获取for update, 因此读操作也被阻塞了, 性能不够好

因此MySQL还可以利用版本号字段, 来实现乐观锁cas, 乐观锁允许cas失败给出提示即可, 因此这有助于减少行锁的占用次数来降低数据库压力

```sql
<update id="updateStockOptimistic">
    update stock
    set count = #{stock.count} ,version = version +1
    where product_id = #{stock.productId} and version = #{stock.version}
</update>
```
这很聪明, 但并不意味着cas就是无锁的, 只要是执行update, 数据库就一定加了行锁, 但是版本号的好处是, 只有update的这一瞬加了锁

执行流程如下: 

- 用索引定位行: 通过 product_id = ? 定位到唯一一行 , 这一步 不加锁, 只是把对象inventory查询出来
- 修改数量字段: newCount
- 执行更新: InnoDB 的规则是, UPDATE 一定是 当前读 current read， 必须尝试加 X 锁，哪怕最后更新 0 行

在依据版本号执行sql过程中, 判断 version : 如果当前行的 version != ? 条件不成立 , 则表示失败, 不修改数据

关键点来了 失败的 UPDATE： 不会进入修改阶段 ,不会持有锁到事务结束, 立即释放锁, 锁生命周期极短（微秒级）

一次cas失败后可以搭配重试机制，并且你可以设置重试次数避免无限等待，有两种重试方案：
- 递归重试, 重试整个函数tryDeductByRecursive
- 自旋重试, 只重试更新接口baseMapper.updateStockOptimistic(stock)

在实际业务场景中, 更新库存函数tryDeductByRecursive会存在很多校验, 这些校验往往在重试的过程中需要重复校验, 因此递归重试会更合适一点
```java
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
        } catch (Exception e) {
            throw new RuntimeException("getOne库存异常");
        }
    }
```
## redis锁

分布式锁最佳实践

- 重试: 自旋或者递归
- 防死锁1:加过期时间
- 防死锁2:保证加锁与过期时间的原子性
- 防误删: 唯一标识
- 防误删: 保证判断锁与删锁锁的原子性
- 可重入
- 可续期

## zk锁

分布式锁最佳实践

详见代码

## 并发测试
如果jemeter模拟高并发发现库存扣减失败, 很有可能是你本机tomcat不足以支撑这么高的并发, 请求失败导致的. 

可以单元测试模拟10000个线程来测试,验证最终库存从10000减为0
```java
    @Test
    void contextLoads() {
        CompletableFuture[] completableFutures = new CompletableFuture[10000];
        for (int i = 0; i < 10000; i++) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                //调用扣减接口
            });
            completableFutures[i] = future;
        }
        CompletableFuture<Void> allOf = CompletableFuture.allOf(completableFutures);
        allOf.join();
    
        log.info("end");
    }
```



