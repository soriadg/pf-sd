package mx.ipn.escom.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulador de Clientes para Sistema Financiero Distribuido
 *
 * Parámetros:
 * - n: número de clientes
 * - h: número de hilos (1 < h < 9)
 * - p: pesos iniciales para cada cliente
 * - t: transacciones por minuto (1 < t < 60)
 *
 * Uso: java -jar client-simulator.jar <n> <h> <p> <t>
 * Ejemplo: java -jar client-simulator.jar 10 4 1000 30
 */
public class ClientSimulator {

    private static final String AUTH_URL = "http://localhost:8081/auth";
    private static final String ACCOUNT_URL = "http://localhost:8080/account";

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Random random = new Random();

    private static AtomicLong totalTransactions = new AtomicLong(0);
    private static AtomicLong successfulTransactions = new AtomicLong(0);
    private static AtomicLong failedTransactions = new AtomicLong(0);

    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Uso: java -jar client-simulator.jar <n> <h> <p> <t>");
            System.out.println("  n = número de clientes");
            System.out.println("  h = número de hilos (1 < h < 9)");
            System.out.println("  p = pesos iniciales por cliente");
            System.out.println("  t = transacciones por minuto (1 < t < 60)");
            System.exit(1);
        }

        try {
            int n = Integer.parseInt(args[0]); // número de clientes
            int h = Integer.parseInt(args[1]); // número de hilos
            double p = Double.parseDouble(args[2]); // pesos iniciales
            int t = Integer.parseInt(args[3]); // transacciones por minuto

            if (h <= 1 || h >= 9) {
                System.err.println("Error: h debe estar entre 1 y 9");
                System.exit(1);
            }

            if (t <= 1 || t >= 60) {
                System.err.println("Error: t debe estar entre 1 y 60");
                System.exit(1);
            }

            ClientSimulator simulator = new ClientSimulator();
            simulator.run(n, h, p, t);

        } catch (NumberFormatException e) {
            System.err.println("Error: Todos los parámetros deben ser números válidos");
            System.exit(1);
        }
    }

    public void run(int n, int h, double p, int t) {
        System.out.println("==============================================");
        System.out.println("  SIMULADOR DE CLIENTES - SISTEMA FINANCIERO");
        System.out.println("==============================================");
        System.out.println("Clientes (n): " + n);
        System.out.println("Hilos (h): " + h);
        System.out.println("Depósito inicial (p): $" + p);
        System.out.println("Transacciones/minuto (t): " + t);
        System.out.println("==============================================\n");

        // Crear clientes simulados
        List<SimulatedClient> clients = new ArrayList<>();
        System.out.println("Creando y registrando " + n + " clientes...");

        for (int i = 0; i < n; i++) {
            String curp = generateCURP(i);
            String password = "pass" + i;

            SimulatedClient client = new SimulatedClient(curp, password);

            if (client.register() && client.login()) {
                // Realizar depósito inicial
                if (client.deposit(p)) {
                    clients.add(client);
                    System.out.println("Cliente " + (i + 1) + " registrado: " + curp);
                } else {
                    System.err.println("Error al depositar para cliente: " + curp);
                }
            } else {
                System.err.println("Error al registrar cliente: " + curp);
            }

            // Small delay to avoid overwhelming the server
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (clients.isEmpty()) {
            System.err.println("No se pudo registrar ningún cliente. Terminando.");
            return;
        }

        System.out.println("\n" + clients.size() + " clientes registrados exitosamente.\n");
        System.out.println("Iniciando simulación de transacciones...\n");

        // Calcular intervalo entre transacciones (en milisegundos)
        // t transacciones por minuto = 60000/t milisegundos entre transacciones
        long intervalMs = 60000 / t;

        // Crear executor con h hilos
        ExecutorService executor = Executors.newFixedThreadPool(h);

        // Thread para mostrar estadísticas
        ScheduledExecutorService statsExecutor = Executors.newScheduledThreadPool(1);
        statsExecutor.scheduleAtFixedRate(() -> {
            System.out.println("\n[ESTADÍSTICAS]");
            System.out.println("  Total de transacciones: " + totalTransactions.get());
            System.out.println("  Exitosas: " + successfulTransactions.get());
            System.out.println("  Fallidas: " + failedTransactions.get());
            System.out.println();
        }, 10, 10, TimeUnit.SECONDS);

        // Ejecutar transacciones continuamente
        try {
            while (true) {
                for (SimulatedClient client : clients) {
                    executor.submit(() -> {
                        performRandomTransaction(client, clients);
                    });

                    // Esperar intervalo aleatorio alrededor del intervalo calculado
                    long randomizedInterval = intervalMs + random.nextInt(200) - 100;
                    Thread.sleep(Math.max(randomizedInterval, 100));
                }
            }
        } catch (InterruptedException e) {
            System.out.println("\nSimulación interrumpida. Cerrando...");
        } finally {
            executor.shutdown();
            statsExecutor.shutdown();
        }
    }

    private void performRandomTransaction(SimulatedClient client, List<SimulatedClient> allClients) {
        // Tipos de transacción: 50% transferencias, 30% depósitos, 20% retiros
        int type = random.nextInt(100);

        try {
            if (type < 50 && allClients.size() > 1) {
                // Transferencia a otro cliente aleatorio
                SimulatedClient recipient = allClients.get(random.nextInt(allClients.size()));
                while (recipient.getCurp().equals(client.getCurp())) {
                    recipient = allClients.get(random.nextInt(allClients.size()));
                }

                double amount = 10 + random.nextDouble() * 90; // entre $10 y $100
                boolean success = client.transfer(recipient.getCurp(), amount);

                totalTransactions.incrementAndGet();
                if (success) {
                    successfulTransactions.incrementAndGet();
                } else {
                    failedTransactions.incrementAndGet();
                }

            } else if (type < 80) {
                // Depósito
                double amount = 50 + random.nextDouble() * 200; // entre $50 y $250
                boolean success = client.deposit(amount);

                totalTransactions.incrementAndGet();
                if (success) {
                    successfulTransactions.incrementAndGet();
                } else {
                    failedTransactions.incrementAndGet();
                }

            } else {
                // Retiro
                double amount = 10 + random.nextDouble() * 50; // entre $10 y $60
                boolean success = client.withdraw(amount);

                totalTransactions.incrementAndGet();
                if (success) {
                    successfulTransactions.incrementAndGet();
                } else {
                    failedTransactions.incrementAndGet();
                }
            }
        } catch (Exception e) {
            failedTransactions.incrementAndGet();
        }
    }

    private static String generateCURP(int index) {
        return String.format("SIMC%06d%08d", index, random.nextInt(100000000));
    }

    // ========================================
    // SIMULATED CLIENT CLASS
    // ========================================
    static class SimulatedClient {
        private final String curp;
        private final String password;
        private String token;
        private final CloseableHttpClient httpClient;

        public SimulatedClient(String curp, String password) {
            this.curp = curp;
            this.password = password;
            this.httpClient = HttpClients.createDefault();
        }

        public String getCurp() {
            return curp;
        }

        public boolean register() {
            try {
                HttpPost request = new HttpPost(AUTH_URL + "/register");
                Map<String, String> body = Map.of(
                    "curp", curp,
                    "contrasena", password,
                    "rol", "USUARIO"
                );

                String json = mapper.writeValueAsString(body);
                request.setEntity(new StringEntity(json, org.apache.hc.core5.http.ContentType.APPLICATION_JSON));

                return httpClient.execute(request, response -> {
                    int status = response.getCode();
                    EntityUtils.consume(response.getEntity());
                    return status == 200;
                });
            } catch (Exception e) {
                return false;
            }
        }

        public boolean login() {
            try {
                HttpPost request = new HttpPost(AUTH_URL + "/login");
                Map<String, String> body = Map.of(
                    "curp", curp,
                    "contrasena", password
                );

                String json = mapper.writeValueAsString(body);
                request.setEntity(new StringEntity(json, org.apache.hc.core5.http.ContentType.APPLICATION_JSON));

                return httpClient.execute(request, response -> {
                    if (response.getCode() == 200) {
                        String responseBody = EntityUtils.toString(response.getEntity());
                        Map<String, String> result = mapper.readValue(responseBody, Map.class);
                        token = result.get("token");
                        return token != null;
                    }
                    EntityUtils.consume(response.getEntity());
                    return false;
                });
            } catch (Exception e) {
                return false;
            }
        }

        public boolean deposit(double amount) {
            return performTransaction("/deposit", Map.of("monto", amount));
        }

        public boolean withdraw(double amount) {
            return performTransaction("/withdraw", Map.of("monto", amount));
        }

        public boolean transfer(String toCurp, double amount) {
            return performTransaction("/transfer", Map.of(
                "curpDestino", toCurp,
                "monto", amount
            ));
        }

        private boolean performTransaction(String endpoint, Map<String, Object> body) {
            try {
                HttpPost request = new HttpPost(ACCOUNT_URL + endpoint);
                request.setHeader("Authorization", "Bearer " + token);

                String json = mapper.writeValueAsString(body);
                request.setEntity(new StringEntity(json, org.apache.hc.core5.http.ContentType.APPLICATION_JSON));

                return httpClient.execute(request, response -> {
                    int status = response.getCode();
                    EntityUtils.consume(response.getEntity());
                    return status == 200;
                });
            } catch (Exception e) {
                return false;
            }
        }
    }
}
