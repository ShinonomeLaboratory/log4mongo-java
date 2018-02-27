package org.log4mongo;

import org.bson.Document;
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;

import static org.junit.Assert.assertEquals;

public class TestLoggingEventBsonifierImpl {

    @Test
    public void testStringBuffer() throws NoSuchMethodException, IllegalAccessException,
            InvocationTargetException {
        LoggingEventBsonifierImplSubclass bsonifier = new LoggingEventBsonifierImplSubclass();
        Document bson = new Document();
        String key = "thekey";
        StringBuffer sb = new StringBuffer("thevalue");
        bsonifier.publicNullSafePut(bson, key, sb);
        String retrievedValue = (String) bson.get(key);
        assertEquals(sb.toString(), retrievedValue);
    }

    // Create a subclass so I can test a protected method
    // Replace this after extending Privateer to support superclasses in method signature
    public class LoggingEventBsonifierImplSubclass extends LoggingEventBsonifierImpl {

        public void publicNullSafePut(Document bson, final String key, final Object value) {
            nullSafePut(bson, key, value);
        }

    }
}
