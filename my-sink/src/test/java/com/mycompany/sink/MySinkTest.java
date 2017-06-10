package com.mycompany.sink;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.apache.flume.Channel;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.Sink;
import org.apache.flume.Transaction;
import org.apache.flume.channel.MemoryChannel;
import org.apache.flume.conf.Configurables;
import org.apache.flume.event.EventBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import java.util.HashMap;
import static org.junit.Assert.fail;

public class MySinkTest 
{

    String url = "jdbc:pgsql://localhost:5432/richard";
    Connection conn;

    @Before
    public void setUp() throws Exception, InstantiationException {
        Class.forName("com.impossibl.postgres.jdbc.PGDriver").newInstance();
        conn = DriverManager.getConnection(url, "richard", "richard");
    }

    @After
    public void tearDown() throws Exception {
        if (conn != null) {
            conn.close();
        }
    }

    @Test
    public void evaluatesExpression() throws EventDeliveryException, SQLException {
        Sink mySink = new MySink();
        mySink.setName("My Test Sink");

        Context context = new Context();
        context.put("url", url);
        Configurables.configure(mySink, context);

        Channel memoryChannel = new MemoryChannel();
        Configurables.configure(memoryChannel, context);

        mySink.setChannel(memoryChannel);
        mySink.start();


        Statement stmt = conn.createStatement();
        stmt.executeUpdate("DELETE FROM test");
        stmt.close();

        Transaction txn = memoryChannel.getTransaction();
        txn.begin();
        Event event = EventBuilder.withBody("Dummy Event".getBytes());
        HashMap<String,String> myMap = new HashMap<String,String>();
        myMap.put("path", "test path");
        event.setHeaders(myMap);
        memoryChannel.put(event);
        memoryChannel.put(event);
        memoryChannel.put(event);
        memoryChannel.put(event);
        memoryChannel.put(event);
        memoryChannel.put(event);
        memoryChannel.put(event);
        memoryChannel.put(event);
        memoryChannel.put(event);
        memoryChannel.put(event);
        txn.commit();
        txn.close();

        Sink.Status status = mySink.process();
        if (status == Sink.Status.BACKOFF) {
            fail("Error occured");
        }

        stmt = conn.createStatement();
        ResultSet r = stmt.executeQuery("SELECT COUNT(*) FROM test");
        r.next();
        int result = r.getInt(1);
        stmt.close();

        if (result != 10) {
            fail("Wrong number of records: " + result);
        }
    }
}
