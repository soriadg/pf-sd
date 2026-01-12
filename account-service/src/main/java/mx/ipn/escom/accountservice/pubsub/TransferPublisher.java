package mx.ipn.escom.accountservice.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TransferPublisher {

    private final Publisher publisher;
    private final ObjectMapper mapper = new ObjectMapper();

    public TransferPublisher(
            @Value("${gcp.project-id}") String projectId,
            @Value("${gcp.pubsub.events-topic-id}") String topicId
    ) throws Exception {
        TopicName topicName = TopicName.of(projectId, topicId);
        this.publisher = Publisher.newBuilder(topicName).build();
    }

    public String publish(TransferEvent event) throws Exception {
        String json = mapper.writeValueAsString(event);

        PubsubMessage msg = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(json))
                .putAttributes("type", event.type())
                .putAttributes("txId", event.txId())
                .build();

        ApiFuture<String> futureId = publisher.publish(msg);
        return futureId.get();
    }
}
