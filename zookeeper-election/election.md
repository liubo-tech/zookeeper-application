# Zookeeper应用之——选举（Election）

**_请注意，此篇文章并不是介绍Zookeeper集群中Leader的选举机制，而是应用程序使用Zookeeper作为选举的应用。_**

使用Zookeeper进行选举，主要用到了Znode的两个性质：
1. 临时节点（EPHEMERAL）
2. 序列化节点（SEQUENCE）

每一个临时的序列化节点代表着一个客户端（client），也就是选民。主要的设计思路如下：

首先，创建一个选举的节点，我们叫做/election。
然后，每有一个客户端加入，就创建一个子节点/election/n_xxx，这个节点是EPHEMERAL并且SEQUENCE，xxx就是序列化产生的单调递增的数字。
在所有子节点中，序列数字做小的被选举成Leader。

上面的并不是重点，重点是Leader失败的检测，Leader失败后，一个新的客户端（client）将被选举成Leader。实现这个过程的一个最简单的方式是
所有的客户端（client）都监听Leader节点，一旦Leader节点消失，将通知所有的客户端（client）执行Leader选举过程，序列数字最小的将被选举成Leader。
这样实现看似没有问题，但是当客户端（client）数量非常庞大时，所有客户端（client）都将在/election节点执行getChildren()，这对Zookeeper
的压力是非常大的。为了避免这种“惊群效应”，我们可以让客户端只监听它前一个节点（所有序列数字比当前节点小，并且是其中最大的那个节点）。
这样，Leader节点消失后，哪个节点收到了通知，哪个节点就变成Leader，因为所有节点中，没有比它序列更小的节点了。

具体步骤如下：

1. 使用EPHEMERAL和SEQUENCE创建节点/election/n_xxx，我们叫做z。
2. C为/election的子节点集合，i是z的序列数字。
3. 监听/election/n_j，j是C中小于i的最大数字。

接收到节点消失的事件后：

1. C为新的/election的子节点集合
2. 如果z是集合中最小的节点，则z被选举成Leader
3. 如果z不是最小节点，则继续监听/election/n_j，j是C中小于i的最大数字。

具体代码如下：
```java
public class Candidate implements Runnable, Watcher {
    //zk
    private ZooKeeper zk;
    //临时节点前缀
    private String perfix = "n_";
    //当前节点
    private String currentNode;
    //前一个最大节点
    private String lastNode;

    /**
     * 构造函数
     * @param address zk地址
     */
    public Candidate(String address) {
        try {
            this.zk = new ZooKeeper(address, 3000, this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 加入选举
     */
    @Override
    public void run() {
        try {
            //创建临时节点
            currentNode = zk.create("/zookeeper/election/" + perfix, Thread.currentThread().getName().getBytes(),
                    ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            //选举
            election();
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 从小到大排序临时节点
     * @param children
     * @return
     */
    private List<String> getSortedNode(List<String> children) {
        return children.stream().sorted(((o1, o2) -> {
            String sequence1 = o1.split(perfix)[1];
            String sequence2 = o2.split(perfix)[1];
            BigDecimal decimal1 = new BigDecimal(sequence1);
            BigDecimal decimal2 = new BigDecimal(sequence2);
            int result = decimal1.compareTo(decimal2);
            return result;
        })).collect(toList());
    }

    /**
     * 选举过程
     */
    private void election(){
        try{
            while (true){
                //获取/election节点中的所有子节点
                List<String> children = zk.getChildren("/zookeeper/election", false);
                //所有子节点排序（从小到大）
                List<String> sortedNodes = getSortedNode(children);
                //获取最小节点
                String smallestNode = sortedNodes.get(0);
                //当前节点就是最小节点，被选举成Leader
                if (currentNode.equals("/zookeeper/election/"+smallestNode)) {
                    System.out.println(currentNode + "被选举成Leader。");
                    Thread.sleep(5000);
                    //模拟Leader节点死去
                    System.out.println(currentNode+"已离去");
                    zk.close();
                    break;
                }
                //当前节点不是最小节点，监听前一个最大节点
                else {
                    //前一个最大节点
                    lastNode = smallestNode;
                    //找到前一个最大节点，并监听
                    for (int i = 1; i < sortedNodes.size(); i++) {
                        String z = sortedNodes.get(i);
                        //找到前一个最大节点，并监听
                        if (currentNode.equals("/zookeeper/election/"+z)) {
                            zk.exists("/zookeeper/election/" + lastNode, true);
                            System.out.println(currentNode+"监听"+lastNode);
                            //等待被唤起执行Leader选举
                            synchronized (this){
                                wait();
                            }
                            break;
                        }
                        lastNode = z;
                    }
                }
            }
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 观察器通知
     * @param event
     */
    @Override
    public void process(WatchedEvent event) {
        //监听节点删除事件
        if (event.getType().equals(Event.EventType.NodeDeleted)) {
            //被删除的节点是前一个最大节点，唤起线程执行选举
            if (event.getPath().equals("/zookeeper/election/" + lastNode)) {
                System.out.println(currentNode+"被唤起");
                synchronized (this){
                    notify();
                }
            }
        }
    }
}
```
我们将启动5个线程作为参选者，模拟每一个Leader死去，并重新选举的过程。启动程序如下：
```java
public class Application {

    private static final String ADDRESS = "149.28.37.147:2181";

    public static void main(String[] args) throws InterruptedException {
        setLog();
        ExecutorService es = Executors.newFixedThreadPool(5);
        for (int i=0;i<5;i++){
            es.execute(new Candidate(ADDRESS));
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
运行结果如下：
```
/zookeeper/election/n_0000000133被选举成Leader。
/zookeeper/election/n_0000000134监听n_0000000133
/zookeeper/election/n_0000000137监听n_0000000136
/zookeeper/election/n_0000000135监听n_0000000134
/zookeeper/election/n_0000000136监听n_0000000135
/zookeeper/election/n_0000000133已离去
/zookeeper/election/n_0000000134被唤起
/zookeeper/election/n_0000000134被选举成Leader。
/zookeeper/election/n_0000000134已离去
/zookeeper/election/n_0000000135被唤起
/zookeeper/election/n_0000000135被选举成Leader。
/zookeeper/election/n_0000000135已离去
/zookeeper/election/n_0000000136被唤起
/zookeeper/election/n_0000000136被选举成Leader。
/zookeeper/election/n_0000000136已离去
/zookeeper/election/n_0000000137被唤起
/zookeeper/election/n_0000000137被选举成Leader。
/zookeeper/election/n_0000000137已离去
```
Zookeeper作为选举的应用就介绍完了，项目示例请参考：[https://github.com/liubo-tech/zookeeper-application](https://github.com/liubo-tech/zookeeper-application)。
