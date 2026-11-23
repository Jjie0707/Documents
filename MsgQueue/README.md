# MsgQueue｜轻量级消息队列

用 Java 实现的单机消息队列，包含 Broker 和 Java 客户端。参考 RabbitMQ 的交换机、队列与绑定模型，支持队列声明、消息路由、订阅、消费确认和本地持久化。

## 运行方式

| 组件 | 入口 | 用途 |
| --- | --- | --- |
| Broker | `jjie.msgqueue.MsgQueueApplication` | 启动 Spring Boot 和监听 `9090` 的 TCP Broker |
| 消费者 | `jjie.msgqueue.demo.DemoConsumer` | 订阅 `testQueue`，打印收到的消息 |
| 生产者 | `jjie.msgqueue.demo.DemoProducer` | 建立绑定，读取输入并发送演示消息 |
| SQLite 和队列文件 | `./data/` | 保存持久化拓扑和队列消息 |

项目使用 Java、Spring Boot、TCP Socket、Java I/O、MyBatis-Plus 和 SQLite。演示使用 Java 客户端，经 TCP 连接 Broker。 [Maven 配置](pom.xml) · [应用配置](src/main/resources/application.yaml)

## Mac、Linux 和 Windows 启动

需要 JDK 23 或更新版本。用 `java -version` 确认实际使用的 JDK，从包含 `pom.xml` 的 `MsgQueue/` 目录启动。

```bash
./mvnw spring-boot:run
```

Windows PowerShell 使用 `./mvnw.cmd spring-boot:run`。也可以在 IDEA 导入 Maven 项目，设置 JDK 和工作目录，运行 [MsgQueueApplication](src/main/java/jjie/msgqueue/MsgQueueApplication.java)。Broker 固定监听 `9090`。默认 Spring Boot Web 端口是 `8080`，如有冲突可用应用参数 `--server.port=0` 让系统分配空闲 HTTP 端口。

## 验证顺序

1. 启动 Broker，等待终端出现 `[BrokerServer] 启动!`。
2. 在 IDEA 另启 [DemoConsumer](src/main/java/jjie/msgqueue/demo/DemoConsumer.java)，订阅 `testQueue`。
3. 另启 [DemoProducer](src/main/java/jjie/msgqueue/demo/DemoProducer.java)，输入 `1` 并回车。生产者应显示 `消息投递完成! ok=true`，消费者应收到 `body=hello`。
4. 演示结束后停止两个客户端，再停止 Broker。终端可以按 `Ctrl+C`，IDEA 可以用停止按钮。

可用下面的命令运行协议和 Broker 集成测试，覆盖响应匹配、分段 TCP 响应、连接断开、消息体和手动 ACK。

```bash
./mvnw -Dtest=ClientProtocolTest,ClientBrokerTest test
```

Windows 将 `./mvnw` 换成 `./mvnw.cmd`。运行全量测试时使用独立副本，数据库测试涉及本地 SQLite 的初始化和清理。 [协议测试](src/test/java/jjie/msgqueue/client/ClientProtocolTest.java) · [Broker 集成测试](src/test/java/jjie/msgqueue/client/ClientBrokerTest.java)

## 消息路由与消费确认

| 模式 | 路由规则 |
| --- | --- |
| Direct | 将 `routingKey` 当作队列名，投递到与该交换机绑定的同名队列 |
| Fanout | 投递到交换机绑定的所有队列 |
| Topic | 按 `routingKey` 与 `bindingKey` 的模式匹配结果投递 |

- **连接与协议**。一个 TCP 连接承载多个逻辑 `Channel`。帧依次包含操作类型、载荷长度和载荷。客户端按 `channelId` 分发响应与推送，并通过 `rid` 匹配请求和响应。 [连接实现](src/main/java/jjie/msgqueue/client/Connection.java) · [Broker 实现](src/main/java/jjie/msgqueue/server/BrokerServer.java)
- **异步投递**。队列通知通过阻塞队列交给扫描线程，再由工作线程池投递。同一队列的多个消费者按轮询选取，消息出队和待确认登记在同一队列锁内完成。 [消费者管理](src/main/java/jjie/msgqueue/server/mapper/Manager/ConsumerManager.java)
- **ACK**。自动确认发生在 Broker 写出推送帧后。手动确认由 Java 客户端在处理消息后调用 `basicAck(queueName, messageId)`。 [客户端 Channel](src/main/java/jjie/msgqueue/client/Channel.java) · [虚拟主机](src/main/java/jjie/msgqueue/server/VirtualHost.java)

## 本地持久化与恢复

- `data/msgqueue.db` 保存持久化的交换机、队列和绑定关系。
- `data/<队列名>/data.txt` 追加保存持久化消息的二进制记录，`sta.txt` 保存记录统计。ACK 按偏移标记记录失效，达到清理阈值后重写有效记录并更新内存中的偏移。
- 重启 Broker 时，先恢复拓扑，再扫描持久化队列文件，把仍有效的消息恢复到待投递队列。在独立的项目副本运行演示，可避免改动现有的 `data/` 数据。

当前实现是单机 Broker，使用项目自定义的 TCP 协议。可用仓库附带的 Java 客户端在本机演示。 [文件管理](src/main/java/jjie/msgqueue/server/mapper/Manager/DataFileManager.java) · [内存恢复](src/main/java/jjie/msgqueue/server/mapper/Manager/MemoryDataManager.java)
