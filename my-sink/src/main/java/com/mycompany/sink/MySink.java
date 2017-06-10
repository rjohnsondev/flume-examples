package com.mycompany.sink;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import org.apache.flume.Channel;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.Transaction;
import org.apache.flume.conf.Configurable;
import org.apache.flume.instrumentation.SinkCounter;
import org.apache.flume.sink.AbstractSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MySink extends AbstractSink implements Configurable {

    private static final Logger logger = LoggerFactory.getLogger(MySink.class);
    private SinkCounter sinkCounter;
    private int batchSize = 100;

    private String url;
    private String username;
    private String password;
    private Connection conn;

    @Override
    public void configure(Context context) {
        url = context.getString("url", "jdbc:pgsql://localhost:5432/richard");
        username = context.getString("username", "richard");
        password = context.getString("username", "richard");
        batchSize = context.getInteger("batch-size", 100);
        if (sinkCounter == null) {
            sinkCounter = new SinkCounter(getName());
        }
    }

    private void connect() {
        try {
            Class.forName("com.impossibl.postgres.jdbc.PGDriver").newInstance();
            conn = DriverManager.getConnection(url, username, password);
            conn.setAutoCommit(false);
        } catch (Exception err) {
            sinkCounter.incrementConnectionFailedCount();
            logger.error("Unable to setup connection", err);
        }
    }

    @Override
    public void start() {
        sinkCounter.start();
        connect();
        super.start();
    }

    @Override
    public void stop () {
        if (conn != null) {
            logger.info("Closing connection");
            try {
                conn.close();
                sinkCounter.incrementConnectionClosedCount();
            } catch (Exception err) {
                logger.info("Unable to close connection", err);
            }
        }
        super.stop();
    }

    @Override
    public Status process() throws EventDeliveryException {
        Status status = Status.READY;

        if (conn == null) {
            connect();
            if (conn == null) {
                return Status.BACKOFF;
            }
        }

        logger.debug("processing...");

        Channel channel = getChannel();
        Transaction txn = channel.getTransaction();

        try {
            txn.begin();
            int count;

            PreparedStatement insertRecords = conn.prepareStatement("INSERT INTO test (val, path) VALUES (?, ?)");

            for (count = 0; count < batchSize; ++count) {
                Event event = channel.take();

                if (event == null) {
                    break;
                }

                insertRecords.setString(1, new String(event.getBody(), "utf-8"));
                insertRecords.setString(2, event.getHeaders().get("path"));
                insertRecords.addBatch();
            }

            insertRecords.executeBatch();
            conn.commit();
            insertRecords.close();

            if (count <= 0) {
                sinkCounter.incrementBatchEmptyCount();
                status = Status.BACKOFF;
            }

            sinkCounter.addToEventDrainAttemptCount(count);
            txn.commit();
            sinkCounter.addToEventDrainSuccessCount(count);

        } catch (Exception ex) {
            try {
                txn.rollback();
            } catch (Exception ex2) {
                logger.error(
                        "Exception in rollback. Rollback might not have been successful.",
                        ex2);
            }
            logger.error("Failed to commit transaction. Transaction rolled back.", ex);
            throw new EventDeliveryException(
                    "Failed to commit transaction. Transaction rolled back.", ex);
        } finally {
            txn.close();
        }
        return status;

    }

}
