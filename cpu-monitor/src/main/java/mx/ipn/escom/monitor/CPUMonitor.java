package mx.ipn.escom.monitor;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.*;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HardwareAbstractionLayer;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Monitor de CPU con interfaz Lanterna
 *
 * Muestra el uso de CPU de las instancias del sistema distribuido
 * en intervalos de n segundos.
 *
 * Uso: java -jar cpu-monitor.jar <n>
 * Donde n = segundos entre actualizaciones (default: 5)
 */
public class CPUMonitor {

    private final int intervalSeconds;
    private final SystemInfo systemInfo;
    private final HardwareAbstractionLayer hal;
    private final CentralProcessor processor;

    private final Map<String, ServiceInstance> instances = new LinkedHashMap<>();
    private long[] prevTicks;

    public static void main(String[] args) {
        int interval = 5; // default 5 seconds

        if (args.length > 0) {
            try {
                interval = Integer.parseInt(args[0]);
                if (interval < 1) {
                    System.err.println("El intervalo debe ser mayor a 0. Usando 5 segundos por defecto.");
                    interval = 5;
                }
            } catch (NumberFormatException e) {
                System.err.println("Argumento inválido. Usando 5 segundos por defecto.");
            }
        }

        try {
            CPUMonitor monitor = new CPUMonitor(interval);
            monitor.start();
        } catch (Exception e) {
            System.err.println("Error al iniciar monitor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public CPUMonitor(int intervalSeconds) {
        this.intervalSeconds = intervalSeconds;
        this.systemInfo = new SystemInfo();
        this.hal = systemInfo.getHardware();
        this.processor = hal.getProcessor();
        this.prevTicks = processor.getSystemCpuLoadTicks();

        initializeInstances();
    }

    private void initializeInstances() {
        // Initialize service instances
        instances.put("account-service-1", new ServiceInstance("account-service", "localhost:8080"));
        instances.put("auth-service-1", new ServiceInstance("auth-service", "localhost:8081"));
        instances.put("transaction-service-1", new ServiceInstance("transaction-service", "localhost:8083"));
        instances.put("report-service-1", new ServiceInstance("report-service", "localhost:8084"));
        instances.put("web-interface-1", new ServiceInstance("web-interface", "localhost:8085"));
    }

    public void start() throws IOException {
        // Create terminal and screen
        DefaultTerminalFactory terminalFactory = new DefaultTerminalFactory();
        Terminal terminal = terminalFactory.createTerminal();
        Screen screen = new TerminalScreen(terminal);
        screen.startScreen();

        // Create GUI
        MultiWindowTextGUI gui = new MultiWindowTextGUI(screen, new DefaultWindowManager(), new EmptySpace(TextColor.ANSI.BLUE));

        // Create main window
        BasicWindow window = new BasicWindow("Monitor de CPU - Sistema Financiero Distribuido");
        window.setHints(Arrays.asList(Window.Hint.FULL_SCREEN));

        Panel mainPanel = new Panel();
        mainPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));

        // Title
        Label titleLabel = new Label("=== MONITOR DE USO DE CPU ===");
        titleLabel.setForegroundColor(TextColor.ANSI.CYAN);
        mainPanel.addComponent(titleLabel);

        Label infoLabel = new Label("Intervalo de actualización: " + intervalSeconds + " segundos");
        infoLabel.setForegroundColor(TextColor.ANSI.YELLOW);
        mainPanel.addComponent(infoLabel);

        mainPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

        // CPU Summary
        Label cpuSummaryLabel = new Label("CPU Total del Sistema: Calculando...");
        cpuSummaryLabel.setForegroundColor(TextColor.ANSI.GREEN);
        mainPanel.addComponent(cpuSummaryLabel);

        mainPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

        // Table for service instances
        Table<String> table = new Table<>("Servicio", "IP:Puerto", "CPU %", "Estado");
        mainPanel.addComponent(table);

        mainPanel.addComponent(new EmptySpace(new TerminalSize(0, 1)));

        // Footer
        Label footerLabel = new Label("Presiona 'Q' para salir");
        footerLabel.setForegroundColor(TextColor.ANSI.WHITE);
        mainPanel.addComponent(footerLabel);

        window.setComponent(mainPanel);

        // Background updater
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(() -> {
            try {
                updateCPUData();

                gui.getGUIThread().invokeLater(() -> {
                    // Update system CPU
                    long[] ticks = processor.getSystemCpuLoadTicks();
                    double cpuLoad = processor.getSystemCpuLoadBetweenTicks(prevTicks) * 100;
                    prevTicks = ticks;

                    cpuSummaryLabel.setText(String.format("CPU Total del Sistema: %.2f%%", cpuLoad));

                    // Update table
                    table.getTableModel().clear();
                    for (ServiceInstance instance : instances.values()) {
                        table.getTableModel().addRow(
                            instance.getServiceName(),
                            instance.getIp(),
                            String.format("%.2f%%", instance.getCpuUsage()),
                            instance.getStatus()
                        );
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);

        // Show window
        gui.addWindowAndWait(window);

        // Cleanup
        executor.shutdown();
        try {
            screen.stopScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateCPUData() {
        // In a real implementation, this would query each service instance
        // For this simulation, we'll generate realistic CPU usage data
        Random random = new Random();

        for (ServiceInstance instance : instances.values()) {
            // Simulate CPU usage between 5% and 95%
            double baseCpu = 10 + random.nextDouble() * 60;

            // Add some variation
            double currentCpu = instance.getCpuUsage();
            if (currentCpu > 0) {
                // Smooth changes
                double change = (random.nextDouble() - 0.5) * 10;
                baseCpu = Math.max(5, Math.min(95, currentCpu + change));
            }

            instance.setCpuUsage(baseCpu);
            instance.setStatus("Activo");
        }
    }

    // ========================================
    // SERVICE INSTANCE CLASS
    // ========================================
    static class ServiceInstance {
        private final String serviceName;
        private final String ip;
        private double cpuUsage;
        private String status;

        public ServiceInstance(String serviceName, String ip) {
            this.serviceName = serviceName;
            this.ip = ip;
            this.cpuUsage = 0.0;
            this.status = "Iniciando...";
        }

        public String getServiceName() {
            return serviceName;
        }

        public String getIp() {
            return ip;
        }

        public double getCpuUsage() {
            return cpuUsage;
        }

        public void setCpuUsage(double cpuUsage) {
            this.cpuUsage = cpuUsage;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }

}
