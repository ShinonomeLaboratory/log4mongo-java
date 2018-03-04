# Log4Mongo

Log4Mongo for Shinonome Lab

[东云研究所官网——東雲研究所の、今日も平和です～](http://www.shinonome-lab.com/)

<img src="https://vignette.wikia.nocookie.net/nichijou/images/2/2a/ShinonomeHousehold.jpg/revision/latest?cb=20140103102634"></img>

## Introduction
This project forked from：[RobertStewart/log4mongo-java](https://github.com/RobertStewart/log4mongo-java), thanks **RobertStewart**.

Major new features updated:

1. <del>Support batch write for higher performance;</del>
2. Support timeout, delete logs automatically after expired, and you can set different timeout for different log level.
3. Support auto index creation for higher query performance, but the shard-index should be set manually by different senses for higher performance and avoid unbalance between different shards in cluster mode;
4. support create different collection by day/month/user-defined information/hour to avoid single collection too large (not recommended on my point)
5. Use new data type for robust and performance
6. Support compress while transferring data to mongodb
7. Save logs on in a buffer while writing failed maybe caused by network interruption or mongodb crash.
8. Try to JSONize message if message starts with "{" or "["
9. Solve conflict to SLF4J 

## Document


## TODOs

- Write more unit tests
- Write an log analysis & search application with python3+django2
- Write BSON information into Kafka/RabbitMQ/...
