package com.example.zookeeper.election;

import org.apache.zookeeper.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static java.util.stream.Collectors.toList;

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
