# ZooKeeper简介

## ZooKeeper：分布式应用的协调服务

ZooKeeper是一个分布式的开源协调服务，用于分布式应用程序。它公开了一组简单的原子操作，分布式应用程序可以构建这些原子操作，以实现更高级别的服务，以实现同步，配置维护以及组和命名。
它的设计易于编程，并使用在熟悉的文件系统目录树结构之后设计的数据模型。它运行在Java中，并且对Java和C都有绑定。

周所周知，协调服务是很难做到的。它们特别容易出现诸如竞态条件和死锁等错误。ZooKeeper背后的动机是减轻分布式应用程序从头开始实施协调服务的责任。

## 设计目标

**Zookeeper**是简单的。Zookeeper允许分布式进程之间彼此协调，通过一个共享的分级命名空间，它非常像标准的文件系统。

ZooKeeper实现非常重视高性能，高可用性，严格有序的访问。ZooKeeper的性能方面意味着它可以在大型分布式系统中使用。
可靠性方面使其不会成为单点故障。严格的排序意味着可以在客户端实现复杂的同步原子操作。

**Zookeeper是可复制的。** 与它协调的分布式进程一样，ZooKeeper本身也可以在称为集合的一组主机上进行复制。

![image](http://ouip1glzq.bkt.clouddn.com/20180702150337.png)              

组成ZooKeeper服务的服务器必须彼此了解。它们保持状态的内存映像，以及持久存储中的事务日志和快照。只要大多数服务器可用，ZooKeeper服务就可用。
客户端连接到单个ZooKeeper服务器。客户端维护一个TCP连接，通过它发送请求，获取响应，获取观看事件并发送心跳。如果与服务器的TCP连接中断，则客户端将连接到其他服务器。

**Zookeeper是有序的。** ZooKeeper使用反映所有ZooKeeper事务顺序的数字标记每个更新。后续操作可以使用该顺序来实现更高级别的抽象，例如同步原子操作。

**Zookeeper是非常快的。** 它在“读取主导”工作负载中速度特别快。ZooKeeper应用程序在数千台计算机上运行，​​并且在读取比写入更常见的情况下表现最佳，比率大约为10：1。

## 数据模型和分层名称空间

ZooKeeper提供的名称空间非常类似于标准文件系统。名称是由斜线（/）分隔的一系列路径元素。ZooKeeper名称空间中的每个节点都由一个路径标识。

![image2](http://ouip1glzq.bkt.clouddn.com/blog/20180702151527.png)

## 节点和临时节点

与标准文件系统不同的是，ZooKeeper命名空间中的每个节点都可以拥有与其相关的数据以及子级。这就像拥有一个允许文件也是目录的文件系统。（ZooKeeper旨在存储协调数据：状态信息，配置，位置信息等，因此存储在每个节点的数据通常很小，在字节到千字节范围内。）我们使用术语 znode来表明我们正在谈论ZooKeeper数据节点。
   
Znodes维护一个stat结构，包括数据更改，ACL更改和时间戳的版本号，以允许缓存验证和协调更新。每次znode的数据更改时，版本号都会增加。例如，每当客户端检索数据时，它也会收到数据的版本。
   
存储在名称空间中每个节点上的数据是以原子方式读取和写入的。读取获取与znode关联的所有数据字节，写入将替换所有数据。每个节点都有一个访问控制列表（ACL），限制谁可以做什么。
   
ZooKeeper也有临时节点的概念。只要创建znode的会话处于活动状态，就会存在这些znode。当会话结束时，znode被删除。

## 有条件的更新和监视
 
ZooKeeper支持观察的概念。客户可以在znode上设置观察器。当znode更改时，将触发并删除观察器。
当观察被触发时，客户端收到一个数据包，说明znode已经改变。如果客户端和其中一个Zoo Keeper服务器之间的连接断开，客户端将收到本地通知。

## 担保

ZooKeeper非常快速且非常简单。但是，由于其目标是构建更复杂的服务（如同步）的基础，因此它提供了一系列保证。这些是：

* 顺序一致性 - 客户端的更新将按照它们发送的顺序进行应用。

* 原子性 - 更新成功或失败。没有部分结果。

* 单系统映像 - 无论服务器连接到哪个服务器，客户端都会看到相同的服务视图。

* 可靠性 - 一旦应用更新，它将一直持续到客户覆盖更新为止。

* 及时性 - 系统的客户视图保证在特定时间范围内是最新的。

## 简单的API

ZooKeeper的设计目标之一是提供一个非常简单的编程接口。因此，它仅支持以下操作：

* 创建——在树中的某个位置创建一个节点
* 删除——删除节点
* 存在——测试某个位置是否存在节点
* 获取数据——从节点读取数据
* 设定数据——将数据写入节点
* 得到子节点——检索节点的子节点列表
* 同步——等待数据传播

利用Zookeeper我们可以实现很多解决方案，我们会在以后的篇幅中介绍。

* [Zookeeper应用之——栅栏（barrier）](/zookeeper-barrier/zookeeper-barrier.md)
* [Zookeeper应用之——队列（Queue）](/zookeeper-queue/zookeeper-queue.md)
* [Zookeeper应用之——选举（Election）](/zookeeper-election/election.md)
* [Zookeeper应用之——锁（Lock）](/zookeeper-lock/lock.md)