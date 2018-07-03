package com.example.zookeeper.barrier;

import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;

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
