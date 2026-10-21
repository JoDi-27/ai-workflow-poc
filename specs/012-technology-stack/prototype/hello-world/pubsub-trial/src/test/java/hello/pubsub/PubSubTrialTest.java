package hello.pubsub;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcCallContext;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.DeadlineExceededException;
import java.time.Duration;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.cloud.pubsub.v1.stub.GrpcSubscriberStub;
import com.google.cloud.pubsub.v1.stub.SubscriberStubSettings;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.DeadLetterPolicy;
import com.google.pubsub.v1.ModifyAckDeadlineRequest;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.ReceivedMessage;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.TopicName;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.gcloud.PubSubEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Spike 012 trial: the event feed's Pub/Sub pattern (spec 010 AD-24) on the emulator. */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PubSubTrialTest {

    static final String PROJECT = "trial";

    @Container
    static PubSubEmulatorContainer emulator =
            new PubSubEmulatorContainer("gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators");

    static ManagedChannel channel;
    static TransportChannelProvider channels;
    static GrpcSubscriberStub subscriber;

    @BeforeAll
    static void connect() throws Exception {
        channel = ManagedChannelBuilder.forTarget(emulator.getEmulatorEndpoint()).usePlaintext().build();
        channels = FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
        subscriber = GrpcSubscriberStub.create(SubscriberStubSettings.newBuilder()
                .setTransportChannelProvider(channels).setCredentialsProvider(NoCredentialsProvider.create()).build());
        try (var topics = TopicAdminClient.create(TopicAdminSettings.newBuilder()
                .setTransportChannelProvider(channels).setCredentialsProvider(NoCredentialsProvider.create()).build());
             var subs = SubscriptionAdminClient.create(SubscriptionAdminSettings.newBuilder()
                .setTransportChannelProvider(channels).setCredentialsProvider(NoCredentialsProvider.create()).build())) {
            topics.createTopic(TopicName.of(PROJECT, "events"));
            topics.createTopic(TopicName.of(PROJECT, "events-dead"));
            subs.createSubscription(Subscription.newBuilder()
                    .setName(sub("analysis").toString())
                    .setTopic(TopicName.of(PROJECT, "events").toString())
                    .setEnableMessageOrdering(true)
                    .setAckDeadlineSeconds(10)
                    .setDeadLetterPolicy(DeadLetterPolicy.newBuilder()
                            .setDeadLetterTopic(TopicName.of(PROJECT, "events-dead").toString())
                            .setMaxDeliveryAttempts(5))
                    .build());
            subs.createSubscription(Subscription.newBuilder()
                    .setName(sub("unordered").toString())
                    .setTopic(TopicName.of(PROJECT, "events").toString())
                    .setAckDeadlineSeconds(10)
                    .setDeadLetterPolicy(DeadLetterPolicy.newBuilder()
                            .setDeadLetterTopic(TopicName.of(PROJECT, "events-dead").toString())
                            .setMaxDeliveryAttempts(5))
                    .build());
            subs.createSubscription(sub("dead"), TopicName.of(PROJECT, "events-dead"),
                    com.google.pubsub.v1.PushConfig.getDefaultInstance(), 10);
        }
    }

    @AfterAll
    static void close() throws Exception {
        subscriber.close();
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    static ProjectSubscriptionName sub(String name) {
        return ProjectSubscriptionName.of(PROJECT, name);
    }

    static void publish(String... runAndEvent) throws Exception {
        var publisher = Publisher.newBuilder(TopicName.of(PROJECT, "events"))
                .setChannelProvider(channels).setCredentialsProvider(NoCredentialsProvider.create())
                .setEnableMessageOrdering(true).build();
        try {
            var futures = new ArrayList<com.google.api.core.ApiFuture<String>>();
            for (var re : runAndEvent) {
                var run = re.split(":")[0];
                futures.add(publisher.publish(PubsubMessage.newBuilder().setOrderingKey(run)
                        .putAttributes("event_id", re).setData(ByteString.copyFromUtf8(re)).build()));
            }
            for (var f : futures) f.get(10, TimeUnit.SECONDS);
        } finally {
            publisher.shutdown();
            publisher.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    static List<ReceivedMessage> pull(String subscription, int max) {
        var out = new ArrayList<ReceivedMessage>();
        for (int i = 0; i < 5 && out.size() < max; i++) {
            try {
                out.addAll(subscriber.pullCallable().call(PullRequest.newBuilder()
                        .setSubscription(sub(subscription).toString()).setMaxMessages(max - out.size()).build(),
                        GrpcCallContext.createDefault().withTimeoutDuration(Duration.ofSeconds(2)))
                        .getReceivedMessagesList());
            } catch (DeadlineExceededException e) {
                // nothing to deliver right now
            }
        }
        return out;
    }

    static List<String> data(List<ReceivedMessage> msgs) {
        return msgs.stream().map(m -> m.getMessage().getData().toStringUtf8()).toList();
    }

    static void ack(String subscription, List<ReceivedMessage> msgs) {
        if (msgs.isEmpty()) return;
        subscriber.acknowledgeCallable().call(AcknowledgeRequest.newBuilder().setSubscription(sub(subscription).toString())
                .addAllAckIds(msgs.stream().map(ReceivedMessage::getAckId).toList()).build());
    }

    static void nack(String subscription, List<ReceivedMessage> msgs) {
        subscriber.modifyAckDeadlineCallable().call(ModifyAckDeadlineRequest.newBuilder()
                .setSubscription(sub(subscription).toString()).setAckDeadlineSeconds(0)
                .addAllAckIds(msgs.stream().map(ReceivedMessage::getAckId).toList()).build());
    }

    @Test
    @Order(1)
    void orderedPerRun() throws Exception {
        publish("A:1", "B:1", "A:2", "B:2", "A:3");
        var got = pull("analysis", 5);
        System.out.println("pulled: " + data(got));
        var a = data(got).stream().filter(s -> s.startsWith("A")).toList();
        var b = data(got).stream().filter(s -> s.startsWith("B")).toList();
        assertThat(a).containsExactly("A:1", "A:2", "A:3");
        assertThat(b).containsExactly("B:1", "B:2");
        ack("analysis", got);
    }

    @Test
    @Order(2)
    void nackRedeliversInOrder() throws Exception {
        publish("C:1", "C:2", "C:3");
        var first = pull("analysis", 3);
        System.out.println("first pull: " + data(first));
        nack("analysis", first.subList(0, 1));             // nack C:1 only, leave C:2, C:3 outstanding
        nack("analysis", first.subList(1, first.size()));
        var again = pull("analysis", 3);
        System.out.println("after nack: " + data(again) + " attempts " + again.stream().map(ReceivedMessage::getDeliveryAttempt).toList());
        assertThat(data(again)).startsWith("C:1");
        ack("analysis", again);
    }

    @Test
    @Order(3)
    void deadLetterAfterMaxAttempts() throws Exception {
        ack("unordered", pull("unordered", 20));   // drain earlier tests' messages
        publish("D:1");
        for (var subscription : List.of("analysis", "unordered")) {
            var dead = List.<ReceivedMessage>of();
            for (int attempt = 1; attempt <= 8 && dead.isEmpty(); attempt++) {
                var got = pull(subscription, 1);
                System.out.println(subscription + " attempt " + attempt + ": " + data(got) + " deliveryAttempt "
                        + got.stream().map(ReceivedMessage::getDeliveryAttempt).toList());
                if (!got.isEmpty()) nack(subscription, got);
                dead = pull("dead", 1);
            }
            System.out.println(subscription + " dead letter: " + data(dead));
            ack("dead", dead);
        }
    }
}
