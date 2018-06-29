package com.example.zookeeper.barrier;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;

public class BarrierExample implements Runnable,Watcher {

    private ZooKeeper zk;

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
            Stat stat = zk.exists("/zookeeper/barrier", true);
            if (stat!=null){
                System.out.println(Thread.currentThread().getName()+"——barrier节点存在，线程等待");
                synchronized (this){
                    this.wait();
                }
            }
            businessCode();
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void businessCode() {
        try {
            Thread.sleep(2000);
            System.out.println(Thread.currentThread().getName()+"——执行业务逻辑耗时：2s");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void process(WatchedEvent event) {
        if (event.getType() != Event.EventType.NodeDeleted) return;
        try {
            Stat stat = zk.exists("/zookeeper/barrier", true);
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
