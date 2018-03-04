package org.log4mongo;

import org.apache.log4j.spi.LoggingEvent;
import org.bson.Document;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This appender is designed so you can add top level elements to each logging entry. Users can also
 * extend MongoDbAppender themselves in order to add the top level elements.
 * <p>
 * Use case: A desire to use a common appender for unified logs across different code bases, such
 * that commonly logged elements be consistent, such as application, eventType, etc. This is enabled
 * by adding a property called rootLevelProperties with a key=value list of elements to be added to
 * the root level log. See log4j.properties.sample for an example.
 *
 * @author Mick Knutson (http://www.baselogic.com)
 */
public class ExtendedMongoDbAppender extends MongoDbAppender {

    private Document constants;

    private Map<String, String> rootProperties = new LinkedHashMap<>();

    @Override
    protected String getCollectionName() {
        String collectionNameGenerated = super.getCollectionName();
        for (Map.Entry<String, String> kv : rootProperties.entrySet()) {
            collectionNameGenerated = collectionNameGenerated.replaceAll(
                    String.format(
                            "__EXT_%s__",
                            kv.getKey().toUpperCase()
                    ),
                    kv.getValue()
            );
        }
        return collectionNameGenerated;
    }

    /**
     * @see org.apache.log4j.AppenderSkeleton#activateOptions()
     */
    @Override
    public void activateOptions() {
        super.activateOptions();
        initTopLevelProperties();
    }

    /**
     * Initialize custom top level elements to appear in a log event
     * <p>
     * Allows users to create custom properties to be added to the top level log event.
     */
    public void initTopLevelProperties() {
        constants = new Document();
        if (!rootProperties.isEmpty()) {
            constants.putAll(rootProperties);
        }
    }

    /**
     * This will handle spaces and empty values A = minus- @amp; C=equals= @amp; E==F
     * For XML, must escape the ampersand.
     *
     * @param rootLevelProperties key=value list of elements to be added to the root level log
     */
    public void setRootLevelProperties(String rootLevelProperties) {
        for (String keyValue : rootLevelProperties.split(" *& *")) {
            String[] pairs = keyValue.split(" *= *", 2);
            rootProperties.put(pairs[0], pairs.length == 1 ? "" : pairs[1]);
        }
    }

    /**
     * @param generatedDocument The BSON object to insert into a MongoDB database collection.
     * @param loggingEvent      raw data for external using
     */
    @Override
    public void append(Document generatedDocument, LoggingEvent loggingEvent) {
        if (this.isInitialized() && generatedDocument != null) {
            if (constants != null) {
                generatedDocument.putAll(constants);
            }
            super.append(generatedDocument, loggingEvent);
        }
    }

}
