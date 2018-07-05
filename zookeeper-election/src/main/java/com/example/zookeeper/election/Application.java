package com.example.zookeeper.election;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
