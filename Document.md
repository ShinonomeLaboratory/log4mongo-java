# Document of Log4Mongo-Shinonome

You can use `log4mongo` easily after set up `log4j.properties` and `pom.xml` properly.

## POM Configuration
Use `mvn install` install to local maven after downloaded source files.

Add this dependency to your project(set the version you want).
```xml
<dependency>
    <groupId>org.log4mongo</groupId>
    <artifactId>log4mongo-java-shinonome</artifactId>
</dependency>
```

And these dependencies should be provided if your other dependency didn't contains them.
```xml
<dependencies>
    <dependency>
        <groupId>com.google.guava</groupId>
        <artifactId>guava</artifactId>
        <version>24.0-jre</version>
    </dependency>
    
    <dependency>
        <groupId>log4j</groupId>
        <artifactId>log4j</artifactId>
        <version>1.2.17</version>
    </dependency>
    
    <dependency>
        <groupId>org.mongodb</groupId>
        <artifactId>mongo-java-driver</artifactId>
        <version>3.6.3</version>
    </dependency>
    		
</dependencies>
```

## Appender Configuration
Add an appender can export all your logs to MongoDB with your settings.
```properties
log4j.rootLogger=error, MongoDB
log4j.appender.MongoDB=org.log4mongo.MongoDbAppender
```
There're two recommended appenders, for simple, chooseing `org.log4mongo.MongoDbAppender` is ok.
Select `org.log4mongo.ExtendedMongoDbAppender` for more personality information.

## MongoDB connection configuration
```properties
log4j.appender.MongoDB.hostname=localhost
log4j.appender.MongoDB.port=27017
log4j.appender.MongoDB.databaseName=log4mongo
log4j.appender.MongoDB.collectionName=log

log4j.appender.MongoDB.userName=open
log4j.appender.MongoDB.password=sesame
log4j.appender.MongoDB.authorizeDB=admin
```
The last 3 lines for authorization only.
If `authorizeDB` didn't set , it will use `databaseName` as authorize database.

## MongoDB Write Concern
Mostly, logs are allowed write fail in some conditions, but you can set write concern for more `HENTAI` requirements.
`HENTAI` : If you want to ensure every log has written to database reliably.
The major configuration of write concern is:
```properties
log4j.appender.MongoDB.writeConcern=majority,1000
```
The `majority` is the type of Write Concern and the `1000` timeout setting.
If you don't want to set timeout, use `major` as write concern only is ok.

For more details about write concern, see: [Write Concern](https://docs.mongodb.com/manual/reference/write-concern/index.html)

## Log Expiration Configuration
We can delete expired logs automatically by setting TTL index.
```properties
log4j.appender.MongoDB.timeoutMills=1892160000000,1892160000000,1892160000000,1892160000000,1892160000000,1892160000000
```
There're 6 positive integers split with `,` and they represent expiration time of trace,debug,info,warn,error and fatal logs in unit of milliseconds.

## Collection Index Configuration
```properties
log4j.appender.MongoDB.indexSetting=timestamp:1,level:hashed
```
We can query logs with faster speed by setting `indexSetting` in format of `field name:index type` and spilt different settings by `,`.

For more details see: [Indexes](https://docs.mongodb.com/manual/indexes/index.html)

## Additional Information Configuration
**These configurations only for appender: `org.log4mongo.ExtendedMongoDbAppender`**

We can set our personal information by this option such as application name or other information.

```properties
log4j.appender.MongoDB.rootLevelProperties=applicationName=MyProject&eventType=Development
```
The configuration can be formatted as URL parameters' format. and it will be described to a `Map<String,String>`.
If the field name existed in default fields like `message` or `level`, these settings will overflow default information.

## Collection Name Template

We can let logger change collection name by time or some additional fields.
We use macro to describe format.
```properties
log4j.appender.MongoDB.collectionName=log___HOUR_INFO_____EXT_APPLICATIONNAME__
```
### Timestamp Macro
Append timestamp macro in `collectionName` can change collection while timestamp changed.
- `__HOUR_INFO__` will replace to the time in format of `yyyyMMdd_HH`
- `__DAY_INFO__` will replace to the time in format of `yyyyMMdd`
- `__MONTH_INFO__` will replace to the time in format of `yyyyMM`

### Additional Field Macro

We defined some personal information in `Additional Information Configuration`.
If `applicationName` is defined in value of `MyProject`, the all `__EXT_APPLICATIONNAME__`in `collectionName` will be replaced to `MyProject`.
Upper/lower case is sensitive.