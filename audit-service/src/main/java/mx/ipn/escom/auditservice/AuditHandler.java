package mx.ipn.escom.auditservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.pubsub.v1.PubsubMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class AuditHandler {

    private static final Logger log = LoggerFactory.getLogger(AuditHandler.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final AuditJdbcSink jdbcSink;
    private final AuditGcsJsonlSink gcsSink;

    @Value("${audit.write-db:true}")
    private boolean writeDb;

    @Value("${audit.write-gcs:true}")
    private boolean writeGcs;

    @Value("${audit.strict:true}")
    private boolean strict;

    public AuditHandler(AuditJdbcSink jdbcSink, AuditGcsJsonlSink gcsSink) {
        this.jdbcSink = jdbcSink;
        this.gcsSink = gcsSink;
    }

    public void handle(PubsubMessage message) throws Exception {
        String rawJson = message.getData().toStringUtf8();
        JsonNode root = mapper.readTree(rawJson);

        // ✅ 1) Tomar eventId también de attributes (porque tu publisher lo manda ahí)
        String eventId = firstNonBlank(
                message.getAttributesMap().get("event_id"),
                message.getAttributesMap().get("txId"),
                message.getAttributesMap().get("id"),
                pickFirstText(root, "event_id", "txId", "id", "transaction_id")
        );

        // ✅ 2) Tipo también puede venir por attributes
        String tipo = firstNonBlank(
                message.getAttributesMap().get("tipo"),
                message.getAttributesMap().get("type"),
                pickFirstText(root, "tipo", "type")
        );

        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("El evento no trae event_id/txId/id");
        }
        if (tipo == null || tipo.isBlank()) {
            tipo = "DESCONOCIDO";
        }

        // ✅ JSONL: una línea por evento con metadatos de Pub/Sub
        ObjectNode jsonl = mapper.createObjectNode();
        jsonl.put("received_at", OffsetDateTime.now().toString());
        jsonl.put("pubsub_message_id", message.getMessageId());
        jsonl.put("event_id", eventId);
        jsonl.put("tipo", tipo);
        jsonl.set("payload", root);

        String jsonlLine = mapper.writeValueAsString(jsonl);

        boolean okDb = true;
        boolean okGcs = true;
        Exception dbEx = null;
        Exception gcsEx = null;

        if (writeDb) {
            try {
                jdbcSink.save(eventId, tipo + "_CONFIRMADA", rawJson);
            } catch (Exception e) {
                okDb = false;
                dbEx = e;
                log.error("❌ Falló escritura en BD auditoria (event_id={})", eventId, e);
            }
        }

        if (writeGcs) {
            try {
                gcsSink.appendJsonl(jsonlLine);
            } catch (Exception e) {
                okGcs = false;
                gcsEx = e;
                // ✅ mensaje correcto (tu profe pide auditoria/YYYY-MM-DD.jsonl)
                log.error("❌ Falló escritura en GCS auditoria/YYYY-MM-DD.jsonl (event_id={})", eventId, e);
            }
        }

        // ✅ Modo estricto => si falla algún destino habilitado => reintento
        if (strict) {
            if (writeDb && !okDb) throw dbEx;
            if (writeGcs && !okGcs) throw gcsEx;
        }

        // ✅ No estricto => con que uno funcione, se acepta
        if ((!writeDb || okDb) || (!writeGcs || okGcs)) {
            log.info("📝 Auditoría procesada: event_id={}, db={}, gcs={}", eventId, okDb, okGcs);
            return;
        }

        // ✅ Si ambos fallaron, reintento
        throw new RuntimeException("Falló BD y GCS para event_id=" + eventId);
    }

    private String pickFirstText(JsonNode root, String... keys) {
        for (String k : keys) {
            JsonNode n = root.get(k);
            if (n != null && !n.isNull() && n.isValueNode()) {
                String v = n.asText();
                if (v != null && !v.isBlank()) return v;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
