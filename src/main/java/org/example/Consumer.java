package org.example;

import com.timgroup.statsd.NonBlockingStatsDClientBuilder;
import com.timgroup.statsd.StatsDClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

public class Consumer {
    private final static String DATADOG_LATENCY_METRIC = "kafka.message.latency";

    public void sendStreamToKafka() {

        System.out.println("Starting Kafka Consumer");
        try (var consumer = createKafkaConsumer(); var statsClient = createStatsDClient()) {

            // Subscribe to the topic to consume messages from
            String topic = "kafkaStreamData";
            consumer.subscribe(Collections.singletonList(topic));

            /*var topicPartition1 = new TopicPartition(topic, 0);
            var topicPartition2 = new TopicPartition(topic, 1);
            var topicPartition3 = new TopicPartition(topic, 2);
            consumer.assign(List.of(topicPartition1, topicPartition2, topicPartition3));
            consumer.seekToBeginning(List.of(topicPartition1, topicPartition2, topicPartition3));*/

            // Start consuming messages
            try {
                while (true) {
                    ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(100)); // Timeout in milliseconds
                    for (var record : records) {
                        var endTime = System.currentTimeMillis();
                        var message = record.value();
                        var startTime = bytesToLong(record.headers().lastHeader("kafka.start-time").value());
                        var latency = Math.abs(endTime - startTime);
                        System.out.printf("Message Received. Total bytes: %s and latency(ms): %s\n", message.length, latency);

                        // send metrics to Datadog server
                        statsClient.recordDistributionValue(DATADOG_LATENCY_METRIC, latency);
                    }
                }
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
    }

    private static long bytesToLong(byte[] byteArray) {
        var buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.put(byteArray);
        buffer.flip(); // Prepare the buffer for reading
        return buffer.getLong();
    }

    private KafkaConsumer<String, byte[]> createKafkaConsumer() {
        // Kafka broker connection properties
        var bootstrapServers = "20.97.171.226:9092";

        // Group ID for the consumer
        var groupId = "kafkaStreamData.consumerGroup";

        // Create producer properties
        var properties = new Properties();
        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());

        return new KafkaConsumer<>(properties);
    }

    private StatsDClient createStatsDClient() {
        System.out.println("Connecting to Datadog stats client");
        return new NonBlockingStatsDClientBuilder()
                .prefix("statsD")
                .hostname("localhost")
                .port(8125)
                .processorWorkers(2)
                .prefix("custom")
                .build();
    }
}
