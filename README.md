## mysql分布式锁
MySQL自身有3种分布式锁方案：

单元测试见: [mysqlock](mysqlock/src/test/java/org/lyflexi/mysqlock)

### update瞬时行锁
1. InnoDB存储引擎在执行记录行更新的时候会自动添加排他锁。利用这一特性，我们可以解决超卖问题，具体SQL如下：

jmeter压测8000并发, 验证最终库存从8000减为0, 是安全的
```sql
update stock set count = count - 1 where product_id = 1 and count > 0;
```
但要使用sql来执行count = count - 1保证行锁加在该操作上, 如果在Java层执行count = count - 1需要额外加Java锁.

如果再高的并发验证库存扣减失败, 很有可能是你本机tomcat不足以支撑这么高的并发, 请求失败导致的. 可以单元测试模拟10000个线程来测试,验证最终库存从10000减为0
```java
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
```

## select...for update行锁
2. for update显式加排他锁 + 普通的update语句

虽然直接update也是原子的, 但它是瞬时行为, 只适用于简单的电商场景扣库存, 不适合复杂的业务逻辑计算

要想持有锁直到commit之间保证这一期间的线程安全, 需要先在查询的时候加上for update排他锁，然后执行具体的更新业务逻辑, 比如xxljob
```sql
SELECT lock_name
FROM xxl_job_lock
WHERE lock_name = 'schedule_lock'
FOR UPDATE;

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

---第二个线程, 被阻塞
<update id="updateStock">
    update stock set count = #{stock.count} where product_id = #{stock.productId}
</update>
```

此时再有第二个并发请求对该记录行进行修改, 则会被阻塞. for update生效有两点要求:
- 要求第一个线程for update语句必须在事务中, 也就是不能少@Transactional 
- 要求第二个线程必须是写事务才会出现竟态, 因为MySQL并不阻塞读写并发

```java
/**
 * for update 悲观锁
 * @param productId
 * @return
 */
@GetMapping("/stock/deductForUpdate")
public String deductForUpdate(@RequestParam("productId") Long productId){
    stockService.deductForUpdate(productId);
    return "hello stock deduct！！";
}

/**
 * 被阻塞
 * @param productId
 * @return
 */
@GetMapping("/stock/deductForUpdate/concurentModify")
public String concurentModify(@RequestParam("productId") Long productId){
    stockService.modifyName(productId,"modifyName");
    return "modifyName";
}
```

虽说for update性能不好, 但 XXL-JOB 调度端的真实并发是：

**调度中心来调度任务执行, 而调度中心的实例数 1～3（极少超过 5）**, 所以for update并不会影响xxljob的性能瓶颈, 一个反直觉的事实是阻塞≠慢

其实调度系统最怕的三件事
- 并发调度	任务被执行多次
- 调度丢失	任务没执行
- 主从切换异常	调度混乱

这些 比慢 10ms 严重得多.

**即便你有10 万个任务 也只是扫描任务时无锁 , 只有“抢调度权”这一步加锁**

而且不引入redis等分布式锁也避免额外维护中间件

### 乐观策略version
3. 乐观锁版本号字段
无论是update还是for update都是悲观策略, 性能不够好

其实MySQL可以利用版本号字段, 来实现乐观锁cas, 乐观锁允许cas失败给出提示即可, 因此这有助于减少行锁的占用次数来降低数据库压力

```sql
<update id="updateStockOptimistic">
    update stock
    set count = #{stock.count} ,version = version +1
    where product_id = #{stock.productId} and version = #{stock.version}
</update>
```
**这很聪明, 但并不意味着cas就是无锁的**, 同理Java中的cas用的是硬件层面的锁

其实, CAS 版本号 UPDATE 即使失败，也会“短暂尝试加行锁”，只不过在 InnoDB 中：
- 不会长时间持有锁
- 不会形成排队阻塞
- 对系统吞吐影响极小

执行流程如下: 

1️⃣ 用索引定位行

通过 product_id = ? 定位到唯一一行 , 这一步 不加锁（只是定位）

2️⃣ 尝试加排他锁（X Lock）

InnoDB 的规则是： UPDATE 一定是 当前读 current read， 必须尝试加 X 锁，哪怕最后更新 0 行


3️⃣ 判断 version

如果当前行的 version != ? 条件不成立 , 不修改数据

4️⃣ 立即释放锁

关键点来了 失败的 UPDATE： 不会进入修改阶段 ,不会持有锁到事务结束 ,锁生命周期极短（微秒级）

所以：CAS 的核心价值，正是在“失败时几乎不付出代价”。

如果你要尽可能的保证cas成功, 那么可以搭配重试机制，并且你可以设置重试次数避免无限等待，有两种重试方案：
- 递归重试，由于递归容易OOM
- 自旋重试，类似于redis分布式锁

压力不会凭空消失, 重试会让压力从数据库层转移到了Java服务层

下面讲到redis分布式锁的时候也会用到重试机制，所以你可以看到，其实知识是相通的

MySQL虽然能解决超卖的问题，但是会受限于MySQL软件本身，在高并发的场景下支持不是很理想。

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



