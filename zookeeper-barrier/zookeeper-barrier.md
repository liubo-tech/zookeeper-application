# Zookeeper应用之——栅栏（barrier）

## 栅栏（barrier）简介

barrier的作用是所有的线程等待，知道某一时刻，锁释放，所有的线程同时执行。举一个生动的例子，比如跑步比赛，所有
运动员都要在起跑线上等待，直到枪声响后，所有运动员同时起跑，冲向终点。在这个例子中，所有的运动员就是所有的线程，
枪声是所有线程的共享锁，枪声一响，锁释放，所有线程同时执行。

java的concurrent包中已经为我们提供了barrier的实现，它叫做CyclicBarrier。但是它只能在一个java进程中提供barrier，
在分布式、集群的情况下，java是不能提供barrier的。在分布式、集群的环境下，我们需要借助外部工具实现barrier，今天我们
介绍使用zookeeper实现barrier。

## Zookeeper实现Barrier的方式

Zookeeper的安装，无论是集群还是单点，请参阅其他文档，这里不再赘述了。Zookeeper的基本概念，如：节点、临时节点、
树结构、观察器（watcher）等请参阅上一篇文章，这里也不细讲。

我们通过在Zookeeper设置栅栏节点实现Barrier，节点的名字我们叫做/zookeeper/barrier，具体的逻辑如下：

1. 客户端在Barrier节点上调用exists()方法，并设置观察器
2. 如果Barrier节点不存在，则线程继续执行
3. 如果Barrier节点存在，则线程等待，直到触发观察器事件，并且事件是Barrier节点消失的事件，唤起线程

## 具体程序实现

首先连接Zookeeper，创建Barrier节点，如下：
```
[root@vultr ~]# /opt/zookeeper-3.5.4-beta/bin/zkCli.sh -server localhost:2181

[zk: localhost:2181(CONNECTED) 1] create /zookeeper/barrier
Created /zookeeper/barrier

```
然后我们创建类BarrierExample，如下：
```java
public class BarrierExample implements Runnable,Watcher {

    private ZooKeeper zk;

    /**
     * 构造函数
     * @param address  zookeeper地址
     */
    public BarrierExample(String address){
        try {
            this.zk = new ZooKeeper(address,3000,this);
        } catch (IOException e) {
            e.printStackTrace();
            this.zk = null;
        }
    }

    @Override
    public void run() {
        try {
            //监听Barrier节点，并设置观察器
            Stat stat = zk.exists("/zookeeper/barrier", true);
            //Barrier节点存在，线程等待
            if (stat!=null){
                System.out.println(Thread.currentThread().getName()+"——barrier节点存在，线程等待");
                synchronized (this){
                    this.wait();
                }
            }
            //模拟业务代码
            businessCode();
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 模拟业务代码
     */
    private void businessCode() {
        try {
            Thread.sleep(2000);
            System.out.println(Thread.currentThread().getName()+"——执行业务逻辑耗时：2s");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    /**
     * 观察器
     * @param event
     */
    @Override
    public void process(WatchedEvent event) {
        //不是节点删除事件，返回
        if (event.getType() != Event.EventType.NodeDeleted) return;
        try {
            //查看Barrier节点是否存在
            Stat stat = zk.exists("/zookeeper/barrier", true);
            //Barrier节点消失，唤起所有等待线程
            if (stat==null){
                System.out.println(Thread.currentThread().getName()+"——barrier节点消失，唤起所有线程");
                synchronized (this) {
                    this.notify();
                }
            }
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
```
BarrierExample实现了Runnable和Watcher接口，线程开始时，会调用run()方法，发现Barrier节点存在，线程等待。
然后我们将Barrier节点删除，触发Watch事件，发现Barrier节点已消失，唤起等待的线程。接下来，创建主函数类：

```java
public class Application {
    public static void main(String[] args) {
        //设置log级别为Error
        setLog();

        //创建5个线程
        ExecutorService es = Executors.newFixedThreadPool(5);

        for (int i=0;i<5;i++){
            //执行BarrierExample
            es.execute(new BarrierExample("149.28.37.147:2181"));
        }
        es.shutdown();

        System.out.println("主线程完成！");
    }

    /**
     * 设置log级别为Error
     */
    public static void setLog(){
        //1.logback
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        //获取应用中的所有logger实例
        List<Logger> loggerList = loggerContext.getLoggerList();

        //遍历更改每个logger实例的级别,可以通过http请求传递参数进行动态配置
        for (ch.qos.logback.classic.Logger logger:loggerList){
            logger.setLevel(Level.toLevel("ERROR"));
        }
    }
}
```
我们创建了5个线程，发现Barrier节点，等待，具体执行如下：
1. 运行main()函数，后台打印结果如下：
```
主线程完成！
pool-1-thread-1——barrier节点存在，线程等待
pool-1-thread-5——barrier节点存在，线程等待
pool-1-thread-2——barrier节点存在，线程等待
pool-1-thread-4——barrier节点存在，线程等待
pool-1-thread-3——barrier节点存在，线程等待
```
然后我们将Barrier节点删除：
```
[zk: localhost:2181(CONNECTED) 0] delete /zookeeper/barrier
```
Barrier节点消失，唤起所有等待线程，后台打印结果如下：
```
main-EventThread——barrier节点消失，唤起所有线程
main-EventThread——barrier节点消失，唤起所有线程
main-EventThread——barrier节点消失，唤起所有线程
main-EventThread——barrier节点消失，唤起所有线程
main-EventThread——barrier节点消失，唤起所有线程
pool-1-thread-2——执行业务逻辑耗时：2s
pool-1-thread-1——执行业务逻辑耗时：2s
pool-1-thread-5——执行业务逻辑耗时：2s
pool-1-thread-3——执行业务逻辑耗时：2s
pool-1-thread-4——执行业务逻辑耗时：2s
```
Zookeeper的Barrier实现就介绍完了，项目地址为：[https://github.com/liubo-tech/zookeeper-application/tree/master/zookeeper-barrier](https://github.com/liubo-tech/zookeeper-application/tree/master/zookeeper-barrier)

