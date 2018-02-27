# Log4Mongo

Log4Mongo 之東雲研究所特制版
Log4Mongo for Shinonome Lab
[东云研究所官网——東雲研究所の、今日も平和です～](http://www.shinonome-lab.com/)

## Introduction
This project forked from：[RobertStewart/log4mongo-java](https://github.com/RobertStewart/log4mongo-java), thanks **RobertStewart**.

Major new features:

1. Support batch write for higher performance;
2. Support timeout, delete logs automatically after expired, and you can set different timeout for different log level.
3. Support auto index creation for higher query performance, but the shard-index should be set manually by different senses for higher performance and avoid unbalance between different shards in cluster mode;
4. support create different collection by day/month/application/... to avoid single collection too large (not recommended on my point)
5. Use new data type for robust and performance
6. Support compress while transferring data to mongodb
7. Save logs on in a buffer while writing failed maybe caused by network interruption or mongodb crash.
8. (TODO, will start a single project) Write BSON information into Kafka

