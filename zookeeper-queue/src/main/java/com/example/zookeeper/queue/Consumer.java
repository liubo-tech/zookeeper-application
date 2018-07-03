package com.example.zookeeper.queue;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

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
