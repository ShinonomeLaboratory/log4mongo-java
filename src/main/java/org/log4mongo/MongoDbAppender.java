/*
 * Copyright (C) 2009 Peter Monks (pmonks@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.log4mongo;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoIterable;
import com.mongodb.client.model.IndexOptions;
import org.apache.log4j.Level;
import org.apache.log4j.spi.ErrorCode;
import org.apache.log4j.spi.LoggingEvent;
import org.bson.Document;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Log4J Appender that writes log events into a MongoDB document oriented database. Log events are
 * fully parsed and stored as structured records in MongoDB (this appender does not require, nor use
 * a Log4J layout).
 * <p>
 * The appender does <u>not</u> create any indexes on the data that's stored - it is assumed that if
 * query performance is required, those would be created externally (e.g., in the MongoDB shell or
 * other external application).
 *
 * @author Peter Monks (pmonks@gmail.com)
 * @see <a href="http://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/Appender.html">Log4J
 * Appender Interface</a>
 * @see <a href="http://www.mongodb.org/">MongoDB</a>
 */
public class MongoDbAppender extends BsonAppender {

    private final static String DEFAULT_MONGO_DB_HOSTNAME = "localhost";

    private final static String DEFAULT_MONGO_DB_PORT = "27017";

    private final static String DEFAULT_MONGO_DB_DATABASE_NAME = "log4mongo";

    private final static String DEFAULT_MONGO_DB_COLLECTION_NAME = "logevents";

    private final static String MAX_TTL_MILLS_SETTING = "1892160000000,1892160000000,1892160000000,1892160000000,1892160000000,1892160000000";

    private final static String DEFAULT_INDEX_SETTINGS = "timestamp:1,level:hashed";
    private WriteConcern concern;

    private String hostname = DEFAULT_MONGO_DB_HOSTNAME;

    private String port = DEFAULT_MONGO_DB_PORT;

    private String databaseName = DEFAULT_MONGO_DB_DATABASE_NAME;

    private String collectionName = DEFAULT_MONGO_DB_COLLECTION_NAME;

    private String userName = null;

    private String password = null;

    public String getAuthorizeDB() {
        return authorizeDB;
    }

    public void setAuthorizeDB(String authorizeDB) {
        this.authorizeDB = authorizeDB;
    }

    private String authorizeDB = null;

    private String writeConcern = null;

    public String getIndexSetting() {
        return indexSetting;
    }

    public void setIndexSetting(String indexSetting) {
        this.indexSetting = indexSetting;
    }

    private String indexSetting = DEFAULT_INDEX_SETTINGS;

    private String timeoutMills = MAX_TTL_MILLS_SETTING;

    private MongoClient mongo = null;

    private MongoCollection<Document> collection = null;

    private boolean initialized = false;

    private String lastCollectionName = "";

    private final SimpleDateFormat formatHourInfo = new SimpleDateFormat("yyyyMMdd_HH");
    private final SimpleDateFormat formatDayInfo = new SimpleDateFormat("yyyyMMdd");
    private final SimpleDateFormat formatMonthInfo = new SimpleDateFormat("yyyyMM");


    protected String getCollectionName() {
        final Date nowInfo = new Date();
        return collectionName
                .replaceAll("__HOUR_INFO__", formatHourInfo.format(nowInfo))
                .replaceAll("__DAY_INFO__", formatDayInfo.format(nowInfo))
                .replaceAll("__MONTH_INFO__", formatMonthInfo.format(nowInfo));
    }


    /**
     * @see org.apache.log4j.Appender#close()
     */
    public void close() {

        if (mongo != null) {
            collection = null;
            mongo.close();
        }

    }

    /**
     * @see org.apache.log4j.Appender#requiresLayout()
     */
    public boolean requiresLayout() {
        return (false);
    }

    private long[] timeoutSetting = new long[6];

    protected long getTimeoutSetting(Level loggingLevel) {
        switch (loggingLevel.toString().toLowerCase()) {
            case "fatal":
                return timeoutSetting[5];
            case "error":
                return timeoutSetting[4];
            case "warn":
                return timeoutSetting[3];
            case "info":
                return timeoutSetting[2];
            case "debug":
                return timeoutSetting[1];
            case "trace":
                return timeoutSetting[0];
            default:
                return 1892160000L;
        }
    }

    private final List<Document> dataBuffer = Lists.newArrayList();

    /**
     * @see org.apache.log4j.AppenderSkeleton#activateOptions()
     */
    @Override
    public void activateOptions() {
        try {
            // Close previous connections if reactivating
            if (mongo != null) {
                close();
            }

            MongoCredential credentials = null;
            if (userName != null && userName.trim().length() > 0) {
                credentials = MongoCredential.createCredential(userName,
                        authorizeDB == null ? databaseName : authorizeDB, password.toCharArray());
                password = null;
            }

            MongoClientOptions options = MongoClientOptions.builder()
                    .compressorList(Lists.newArrayList(MongoCompressor.createSnappyCompressor()))
                    .build();

            mongo = getMongo(
                    getServerAddresses(hostname, port),
                    credentials,
                    options
            );

            MongoDatabase database = getDatabase(mongo, databaseName);

            getCollection();

            final String[] splittedTimeout = getTimeoutMills().split(",");
            if (splittedTimeout.length != 6) {
                throw new RuntimeException("Invalid timeout setting, should be 6 positive integer splitted with \",\".");
            }
            for (int i = 0; i < 6; i++) {
                timeoutSetting[i] = Long.parseLong(splittedTimeout[i]);
                if (timeoutSetting[i] <= 0) {
                    throw new RuntimeException("Invalid timeout setting, number should be positive integer.");
                }
            }

            initialized = true;
        } catch (Exception e) {
            errorHandler.error("Unexpected exception while initialising MongoDbAppender.", e,
                    ErrorCode.GENERIC_FAILURE);
        }
    }

    /**
     * @param generatedDocument The BSON representation of a Logging Event that will be stored
     * @param loggingEvent      raw data for external using
     * @see org.log4mongo.BsonAppender#append(Document, LoggingEvent)
     */
    @Override
    public void append(Document generatedDocument, LoggingEvent loggingEvent) {
        if (initialized && generatedDocument != null) {
            try {
                final Date expiredDate = new Date(getTimeoutSetting(loggingEvent.getLevel()) + System.currentTimeMillis());
                generatedDocument.append("log_timeout", expiredDate);
                getCollection().insertOne(generatedDocument);
                if (!dataBuffer.isEmpty()) {
                    getCollection().insertMany(dataBuffer);
                    dataBuffer.clear();
                }
            } catch (MongoException e) {
                dataBuffer.add(generatedDocument);
                errorHandler.error("Failed to insert document to MongoDB", e, ErrorCode.WRITE_FAILURE);
            }
        }
    }

    /*
     * This method could be overridden to provide the DB instance from an existing connection.
     */
    protected MongoDatabase getDatabase(MongoClient mongo, String databaseName) {
        return mongo.getDatabase(databaseName);
    }

    /*
     * This method could be overridden to provide the Mongo instance from an existing connection.
     */
    protected MongoClient getMongo(List<ServerAddress> addresses) {
        if (addresses.size() < 2) {
            return new MongoClient(addresses.get(0));
        } else {
            // Replica set
            return new MongoClient(addresses);
        }
    }

    private MongoClient getMongo(List<ServerAddress> addresses, MongoCredential credential, MongoClientOptions options) {
        if (credential == null) {
            return this.getMongo(addresses);
        }

        if (addresses.size() < 2) {
            return new MongoClient(addresses.get(0), credential, options);
        } else {
            // Replica set
            return new MongoClient(addresses, credential, options);
        }
    }

    /**
     * Note: this method is primarily intended for use by the unit tests.
     *
     * @param collection The MongoDB collection to use when logging events.
     */
    public void setCollection(final MongoCollection<Document> collection) {
        if (collection == null) throw new RuntimeException("collection must not be null");
        this.collection = collection;
    }

    /**
     * @return The hostname of the MongoDB server <i>(will not be null, empty or blank)</i>.
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * @param hostname The MongoDB hostname to set <i>(must not be null, empty or blank)</i>.
     */
    public void setHostname(final String hostname) {
        if (hostname == null) throw new RuntimeException("hostname must not be null");
        if (hostname.trim().length() <= 0) throw new RuntimeException("hostname must not be empty or blank");
        this.hostname = hostname;
    }

    /**
     * @return The port of the MongoDB server <i>(will be greater than 0)</i>.
     */
    public String getPort() {
        return port;
    }

    /**
     * @param port The port to set <i>(must not be null, empty or blank)</i>.
     */
    public void setPort(final String port) {
        if (port == null) throw new RuntimeException("port must not be null");
        if (port.trim().length() <= 0) throw new RuntimeException("port must not be empty or blank");

        this.port = port;
    }

    /**
     * @return The database used in the MongoDB server <i>(will not be null, empty or blank)</i>.
     */
    public String getDatabaseName() {
        return databaseName;
    }

    /**
     * @param databaseName The database to use in the MongoDB server <i>(must not be null, empty or
     *                     blank)</i>.
     */
    public void setDatabaseName(final String databaseName) {
        if (databaseName == null) throw new RuntimeException("database must not be null");
        if (databaseName.trim().length() <= 0) throw new RuntimeException("database must not be empty or blank");

        this.databaseName = databaseName;
    }

    /**
     * @param collectionName The collection used within the database in the MongoDB server <i>(must not be
     *                       null, empty or blank)</i>.
     */
    public void setCollectionName(final String collectionName) {
        if (collectionName == null) throw new RuntimeException("collection must not be null");
        if (collectionName.trim().length() <= 0) throw new RuntimeException("collection must not be empty or blank");

        this.collectionName = collectionName;
    }

    /**
     * @return The userName used to authenticate with MongoDB <i>(may be null)</i>.
     */
    public String getUserName() {
        return userName;
    }

    /**
     * @param userName The userName to use when authenticating with MongoDB <i>(may be null)</i>.
     */
    public void setUserName(final String userName) {
        this.userName = userName;
    }

    /**
     * @param password The password to use when authenticating with MongoDB <i>(may be null)</i>.
     */
    public void setPassword(final String password) {
        this.password = password;
    }

    /**
     * @return the writeConcern setting for Mongo.
     */
    public String getWriteConcern() {
        return writeConcern;
    }

    /**
     * @param writeConcern The WriteConcern setting for Mongo.<i>(may be null). If null, set to default of
     *                     dbCollection's writeConcern.</i>
     */
    public void setWriteConcern(final String writeConcern) {
        this.writeConcern = writeConcern;
        String[] writeConcernConfig = writeConcern.split(",");
        if (writeConcernConfig.length == 1) {
            concern = WriteConcern.valueOf(writeConcern);
        } else if (writeConcernConfig.length == 2) {
            concern = WriteConcern.valueOf(writeConcernConfig[0]).withWTimeout(Long.parseLong(writeConcernConfig[1]), TimeUnit.MILLISECONDS);
        } else {
            throw new RuntimeException("Invalid write concern setting" + writeConcern);
        }
    }

    public WriteConcern getConcern() {
        if (concern == null) {
            concern = getCollection().getWriteConcern();
        }
        return concern;
    }


    /**
     * Returns true if appender was successfully initialized. If this method returns false, the
     * appender should not attempt to log events.
     *
     * @return true if appender was successfully initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * @return The MongoDB collection to which events are logged.
     */
    protected MongoCollection<Document> getCollection() {
        final String currentName = getCollectionName();
        if (!lastCollectionName.equals(currentName)) {
            final MongoDatabase db = getDatabase(mongo, databaseName);
            final Set<String> collectionSet = Sets.newHashSet();
            for (String c : db.listCollectionNames()) {
                collectionSet.add(c);
            }
            if (!collectionSet.contains(currentName)) {
                MongoCollection<Document> coll = db.getCollection(currentName);
                coll.createIndex(new Document("log_timeout", 1), new IndexOptions().expireAfter(0L, TimeUnit.SECONDS));
                for (String indexSet : getIndexSetting().split(",")) {
                    String[] indexSetDetail = indexSet.split(":");
                    if (indexSetDetail.length == 2) {
                        try {
                            int indexInfo = Integer.parseInt(indexSetDetail[1]);
                            coll.createIndex(new Document(indexSetDetail[0], indexInfo));
                        } catch (Exception ex) {
                            coll.createIndex(new Document(indexSetDetail[0], indexSetDetail[1]));
                        }
                    }
                }
            }
            setCollection(db.getCollection(currentName));
            lastCollectionName = currentName;
        }
        if (concern == null) {
            return collection;
        }
        return collection.withWriteConcern(concern);
    }

    /**
     * Returns a List of ServerAddress objects for each host specified in the hostname property.
     * Returns an empty list if configuration is detected to be invalid, e.g.:
     * <ul>
     * <li>Port property doesn't contain either one port or one port per host</li>
     * <li>After parsing port property to integers, there isn't either one port or one port per host
     * </li>
     * </ul>
     *
     * @param hostname Blank space delimited hostnames
     * @param port     Blank space delimited ports. Must specify one port for all hosts or a port per
     *                 host.
     * @return List of ServerAddresses to connect to
     */
    private List<ServerAddress> getServerAddresses(String hostname, String port) {
        List<ServerAddress> addresses = Lists.newArrayList();

        String[] hosts = hostname.split(" ");
        String[] ports = port.split(" ");

        if (ports.length != 1 && ports.length != hosts.length) {
            errorHandler.error(
                    "MongoDB appender port property must contain one port or a port per host",
                    null, ErrorCode.ADDRESS_PARSE_FAILURE);
        } else {
            List<Integer> portNums = getPortNumbers(ports);
            // Validate number of ports again after parsing
            if (portNums.size() != 1 && portNums.size() != hosts.length) {
                errorHandler
                        .error("MongoDB appender port property must contain one port or a valid port per host",
                                null, ErrorCode.ADDRESS_PARSE_FAILURE);
            } else {
                boolean onePort = (portNums.size() == 1);

                int i = 0;
                for (String host : hosts) {
                    int portNum = (onePort) ? portNums.get(0) : portNums.get(i);
                    addresses.add(new ServerAddress(host.trim(), portNum));
                    i++;
                }
            }
        }
        return addresses;
    }

    private List<Integer> getPortNumbers(String[] ports) {
        List<Integer> portNumbers = new ArrayList<>();
        for (String port : ports) {
            try {
                Integer portNum = Integer.valueOf(port.trim());
                if (portNum < 0) {
                    errorHandler.error(
                            "MongoDB appender port property can't contain a negative integer",
                            null, ErrorCode.ADDRESS_PARSE_FAILURE);
                } else {
                    portNumbers.add(portNum);
                }
            } catch (NumberFormatException e) {
                errorHandler.error(
                        "MongoDB appender can't parse a port property value into an integer", e,
                        ErrorCode.ADDRESS_PARSE_FAILURE);
            }

        }
        return portNumbers;
    }


    public String getTimeoutMills() {
        return timeoutMills;
    }

    public void setTimeoutMills(String timeoutMills) {
        this.timeoutMills = timeoutMills;
    }

}
