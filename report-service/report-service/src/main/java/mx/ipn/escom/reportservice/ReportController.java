package mx.ipn.escom.reportservice;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/reports")
@CrossOrigin(origins = "*")
public class ReportController {

    private final JdbcTemplate jdbcTemplate;

    public ReportController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "Report service OK");
    }

    // ============================================
    // SUMMARY DATA FOR ADMIN DASHBOARD
    // ============================================
    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        Map<String, Object> summary = new HashMap<>();

        // Total usuarios
        Integer totalUsuarios = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM usuarios", Integer.class);

        // Total transacciones
        Integer totalTransacciones = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transacciones", Integer.class);

        // Monto total en el sistema
        Double montoTotal = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(saldo_banco + saldo_billetera), 0) FROM cuentas", Double.class);

        // Transacciones hoy
        Integer transaccionesHoy = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transacciones WHERE DATE(creado_en) = CURRENT_DATE", Integer.class);

        summary.put("totalUsuarios", totalUsuarios != null ? totalUsuarios : 0);
        summary.put("totalTransacciones", totalTransacciones != null ? totalTransacciones : 0);
        summary.put("montoTotal", montoTotal != null ? montoTotal : 0.0);
        summary.put("transaccionesHoy", transaccionesHoy != null ? transaccionesHoy : 0);

        return summary;
    }

    // ============================================
    // USERS DATA
    // ============================================
    @GetMapping("/users")
    public List<Map<String, Object>> getAllUsers() {
        String sql = "SELECT curp, saldo_banco, saldo_billetera FROM cuentas ORDER BY (saldo_banco + saldo_billetera) DESC";
        return jdbcTemplate.queryForList(sql);
    }

    // ============================================
    // TRANSACTIONS DATA
    // ============================================
    @GetMapping("/transactions")
    public List<Map<String, Object>> getAllTransactions() {
        String sql = "SELECT event_id, curp_origen, curp_destino, monto, tipo, estado, creado_en " +
                     "FROM transacciones ORDER BY creado_en DESC LIMIT 100";
        return jdbcTemplate.queryForList(sql);
    }

    @GetMapping("/transactions/{curp}")
    public List<Map<String, Object>> getUserTransactions(@PathVariable String curp) {
        String sql = "SELECT event_id, curp_origen, curp_destino, monto, tipo, estado, creado_en " +
                     "FROM transacciones " +
                     "WHERE curp_origen = ? OR curp_destino = ? " +
                     "ORDER BY creado_en DESC";
        return jdbcTemplate.queryForList(sql, curp, curp);
    }

    // ============================================
    // CHART DATA - TRANSACTIONS BY PERIOD
    // ============================================
    @GetMapping("/charts/transactions")
    public Map<String, Object> getTransactionsChartData(@RequestParam(defaultValue = "day") String period) {
        Map<String, Object> chartData = new HashMap<>();
        List<String> labels = new ArrayList<>();
        List<Integer> values = new ArrayList<>();

        String sql;
        switch (period.toLowerCase()) {
            case "week":
                sql = "SELECT DATE(creado_en) as fecha, COUNT(*) as total " +
                      "FROM transacciones " +
                      "WHERE creado_en >= CURRENT_DATE - INTERVAL '7 days' " +
                      "GROUP BY DATE(creado_en) " +
                      "ORDER BY fecha";
                break;

            case "month":
                sql = "SELECT DATE(creado_en) as fecha, COUNT(*) as total " +
                      "FROM transacciones " +
                      "WHERE creado_en >= CURRENT_DATE - INTERVAL '30 days' " +
                      "GROUP BY DATE(creado_en) " +
                      "ORDER BY fecha";
                break;

            case "day":
            default:
                sql = "SELECT TO_CHAR(creado_en, 'HH24:00') as hora, COUNT(*) as total " +
                      "FROM transacciones " +
                      "WHERE creado_en >= NOW() - INTERVAL '24 hours' " +
                      "GROUP BY TO_CHAR(creado_en, 'HH24:00') " +
                      "ORDER BY hora";
                break;
        }

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        for (Map<String, Object> row : results) {
            Object label = period.equals("day") ? row.get("hora") : row.get("fecha");
            labels.add(label != null ? label.toString() : "");

            Object totalObj = row.get("total");
            Integer total = 0;
            if (totalObj instanceof Long) {
                total = ((Long) totalObj).intValue();
            } else if (totalObj instanceof Integer) {
                total = (Integer) totalObj;
            }
            values.add(total);
        }

        chartData.put("labels", labels);
        chartData.put("values", values);

        return chartData;
    }

    // ============================================
    // CHART DATA - AMOUNTS BY PERIOD
    // ============================================
    @GetMapping("/charts/amounts")
    public Map<String, Object> getAmountsChartData(@RequestParam(defaultValue = "day") String period) {
        Map<String, Object> chartData = new HashMap<>();
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();

        String sql;
        switch (period.toLowerCase()) {
            case "week":
                sql = "SELECT DATE(creado_en) as fecha, SUM(monto) as total " +
                      "FROM transacciones " +
                      "WHERE creado_en >= CURRENT_DATE - INTERVAL '7 days' " +
                      "GROUP BY DATE(creado_en) " +
                      "ORDER BY fecha";
                break;

            case "month":
                sql = "SELECT DATE(creado_en) as fecha, SUM(monto) as total " +
                      "FROM transacciones " +
                      "WHERE creado_en >= CURRENT_DATE - INTERVAL '30 days' " +
                      "GROUP BY DATE(creado_en) " +
                      "ORDER BY fecha";
                break;

            case "day":
            default:
                sql = "SELECT TO_CHAR(creado_en, 'HH24:00') as hora, SUM(monto) as total " +
                      "FROM transacciones " +
                      "WHERE creado_en >= NOW() - INTERVAL '24 hours' " +
                      "GROUP BY TO_CHAR(creado_en, 'HH24:00') " +
                      "ORDER BY hora";
                break;
        }

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        for (Map<String, Object> row : results) {
            Object label = period.equals("day") ? row.get("hora") : row.get("fecha");
            labels.add(label != null ? label.toString() : "");

            Object totalObj = row.get("total");
            Double total = 0.0;
            if (totalObj != null) {
                total = Double.parseDouble(totalObj.toString());
            }
            values.add(total);
        }

        chartData.put("labels", labels);
        chartData.put("values", values);

        return chartData;
    }

    // ============================================
    // CHART DATA - TRANSACTION TYPES
    // ============================================
    @GetMapping("/charts/types")
    public Map<String, Object> getTransactionTypesData() {
        Map<String, Object> typeData = new HashMap<>();

        Integer depositos = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transacciones WHERE tipo = 'DEPOSITO'", Integer.class);

        Integer retiros = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transacciones WHERE tipo = 'RETIRO'", Integer.class);

        Integer transferencias = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transacciones WHERE tipo = 'TRANSFERENCIA'", Integer.class);

        typeData.put("depositos", depositos != null ? depositos : 0);
        typeData.put("retiros", retiros != null ? retiros : 0);
        typeData.put("transferencias", transferencias != null ? transferencias : 0);

        return typeData;
    }

    // ============================================
    // CPU MONITORING DATA
    // ============================================
    @GetMapping("/instances")
    public List<Map<String, Object>> getInstances() {
        List<Map<String, Object>> instances = new ArrayList<>();

        instances.add(Map.of(
            "serviceName", "account-service",
            "ip", "localhost:8080",
            "status", "running"
        ));

        instances.add(Map.of(
            "serviceName", "auth-service",
            "ip", "localhost:8081",
            "status", "running"
        ));

        instances.add(Map.of(
            "serviceName", "transaction-service",
            "ip", "localhost:8083",
            "status", "running"
        ));

        instances.add(Map.of(
            "serviceName", "report-service",
            "ip", "localhost:8084",
            "status", "running"
        ));

        return instances;
    }
}
