# Zookeeper应用之——队列（Queue）

为了在Zookeeper中实现分布式队列，首先需要设计一个znode来存放数据，这个节点叫做队列节点，我们的例子中这个节点是```/zookeeper/queue```。
生产者向队列中存放数据，每一个消息都是队列节点下的一个新节点，叫做消息节点。消息节点的命名规则为：queue-xxx，xxx是一个单调
递增的序列，我们可以在创建节点时指定创建模式为PERSISTENT_SEQUENTIAL来实现。这样，生产者不断的向队列节点中发送消息，消息为queue-xxx，
队列中，生产者这一端就解决了，我们具体看一下代码：

###Producer（生产者）
```java
public class Producer implements Runnable,Watcher {

    private ZooKeeper zk;

    public Producer(String address){
        try {
            this.zk = new ZooKeeper(address,3000,null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        int i = 0;
        //每隔10s向队列中放入数据
        while (true){
            try {
                zk.create("/zookeeper/queue/queue-",(Thread.currentThread().getName()+"-"+i).getBytes(),
                        ZooDefs.Ids.OPEN_ACL_UNSAFE,CreateMode.PERSISTENT_SEQUENTIAL);
                Thread.sleep(10000);
                i++;
            } catch (KeeperException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void process(WatchedEvent event) {
    }
}
```
生产者每隔10s向队列中存放消息，消息节点的类型为PERSISTENT_SEQUENTIAL，消息节点中的数据为Thread.currentThread().getName()+"-"+i。

### 消费者

消费者从队列节点中获取消息，我们使用getChildren()方法获取到队列节点中的所有消息，然后获取消息节点数据，消费消息，并删除消息节点。
如果getChildren()没有获取到数据，说明队列是空的，则消费者等待，然后再调用getChildren()方法设置观察者监听队列节点，队列节点发生变化后
（子节点改变），触发监听事件，唤起消费者。消费者实现如下：
```java
public class Consumer implements Runnable,Watcher {
    private ZooKeeper zk;
    private List<String> children;

    public Consumer(String address){
        try {
            this.zk = new ZooKeeper(address,3000,this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        int i = 1;
        while (true){
            try {
                //获取所有子节点
                children = zk.getChildren("/zookeeper/queue", false);
                int size = CollectionUtils.isEmpty(children) ? 0 : children.size();
                System.out.println("第"+i+"次获取数据"+size+"条");

                //队列中没有数据，设置观察器并等待
                if (CollectionUtils.isEmpty(children)){
                    System.out.println("队列为空，消费者等待");
                    zk.getChildren("/zookeeper/queue", true);
                    synchronized (this){
                        wait();
                    }
                }else {
                    //循环获取队列中消息，进行业务处理，并从结果集合中删除
                    Iterator<String> iterator = children.iterator();
                    while (iterator.hasNext()){
                        String childNode = iterator.next();
                        handleBusiness(childNode);
                        iterator.remove();
                    }
                }
            } catch (KeeperException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            i++;
        }
    }

    /**
     * 从节点获取数据，执行业务，并删除节点
     * @param childNode
     */
    private void handleBusiness(String childNode) {
        try {
            Stat stat = new Stat();
            byte[] data = zk.getData("/zookeeper/queue/"+childNode, false, stat);
            String str = new String(data);
            System.out.println("获取节点数据："+str);
            zk.delete("/zookeeper/queue/"+childNode,-1);
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


    }

    /**
     * 子节点发生变化，且取得结果为空时，说明消费者等待，唤起消费者
     * @param event
     */
    @Override
    public void process(WatchedEvent event) {
        if (event.getType().equals(Event.EventType.NodeChildrenChanged)){
            synchronized (this){
                notify();
            }
        }
    }
}
```
上面的例子中有一个局限性，就是 **消费者只能有一个** 。队列的用户有两个：广播和队列。

* 广播是所有消费者都拿到消息并消费，我们的例子在删除消息节点时，不能保证其他消费者都拿到了这个消息。
* 队列是一个消息只能被一个消费者消费，我们的例子中，消费者获取消息时，并没有加锁。

所以我们只启动一个消费者来演示，主函数如下：

```java
public class Application {

    private static final String ADDRESS = "149.28.37.147:2181";

    public static void main(String[] args) {
        //设置日志级别
        setLog();

        //启动一个消费者
        new Thread(new Consumer(ADDRESS)).start();

        //启动4个生产者
        ExecutorService es = Executors.newFixedThreadPool(4);
        for (int i=0;i<4;i++){
            es.execute(new Producer(ADDRESS));
        }
        es.shutdown();

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
后台打印结果如下：
```
第1次获取数据2条
获取节点数据：pool-1-thread-4-118
获取节点数据：pool-1-thread-1-0
第2次获取数据3条
获取节点数据：pool-1-thread-4-0
获取节点数据：pool-1-thread-2-0
获取节点数据：pool-1-thread-3-0
第3次获取数据0条
队列为空，消费者等待
第4次获取数据4条
获取节点数据：pool-1-thread-3-1
获取节点数据：pool-1-thread-1-1
获取节点数据：pool-1-thread-4-1
获取节点数据：pool-1-thread-2-1
```

Zookeeper实现队列就介绍完了，项目地址：[https://github.com/liubo-tech/zookeeper-application](https://github.com/liubo-tech/zookeeper-application)。
