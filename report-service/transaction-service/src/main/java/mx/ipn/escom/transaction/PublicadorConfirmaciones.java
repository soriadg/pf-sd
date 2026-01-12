package mx.ipn.escom.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class PublicadorConfirmaciones {

    @Value("${gcp.project-id}")
    private String projectId;

    @Value("${gcp.pubsub.confirmed-topic-id}")
    private String confirmedTopicId;

    private Publisher publisher;
    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    public void iniciar() throws Exception {
        ProjectTopicName topicName = ProjectTopicName.of(projectId, confirmedTopicId);
        publisher = Publisher.newBuilder(topicName).build();
        System.out.println("✔ PublicadorConfirmaciones listo. Topic: " + confirmedTopicId);
    }

    @PreDestroy
    public void cerrar() throws Exception {
        if (publisher != null) {
            publisher.shutdown();
            publisher.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    public void publicarConfirmacion(String eventId,
                                     String tipo,
                                     String curpOrigen,
                                     String curpDestino,
                                     double monto,
                                     String payloadOriginal) throws Exception {

        Map<String, Object> payload = new HashMap<>();
        payload.put("event_id", eventId);
        payload.put("tipo_evento", "TRANSACCION_CONFIRMADA");
        payload.put("tipo", tipo);
        payload.put("curp_origen", curpOrigen);
        payload.put("curp_destino", curpDestino);
        payload.put("monto", monto);
        payload.put("timestamp", Instant.now().toString());
        payload.put("payload_original", payloadOriginal);

        String json = mapper.writeValueAsString(payload);

        PubsubMessage msg = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(json))
                .putAttributes("event_id", eventId)
                .putAttributes("tipo_evento", "TRANSACCION_CONFIRMADA")
                .build();

        ApiFuture<String> fut = publisher.publish(msg);
        fut.get(10, TimeUnit.SECONDS);
    }
}
