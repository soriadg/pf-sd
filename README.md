# Sistema Financiero de Dinero Electrónico - Proyecto Final

## Resumen Ejecutivo

Sistema distribuido de dinero electrónico implementado con microservicios REST en Java, arquitectura event-driven con Google Cloud Pub/Sub, y despliegue en Google Cloud Platform.

## Componentes Completados ✅

### Microservicios Backend (Java + Spring Boot)

1. **AuthService** (Puerto 8081) - Autenticación JWT
2. **AccountService** (Puerto 8080) - Gestión de cuentas
3. **TransactionService** - Procesamiento asíncrono vía Pub/Sub
4. **AuditService** - Auditoría de transacciones
5. **ReportService** (Puerto 8084) - APIs de reportes y métricas

### Interfaces Web

6. **WebInterface** (Puerto 8085)
   - **Interfaz de Usuario:** `/index.html` - Registro, login, depósitos, retiros, transferencias, historial
   - **Panel de Administrador:** `/admin.html` - Balances, gráficos, logs, estadísticas

### Herramientas de Simulación

7. **ClientSimulator** - Simulador de n clientes con h hilos
8. **CPUMonitor** - Monitor TUI con Lanterna para visualizar uso de CPU

## Requisitos del Proyecto (Checklist)

- [x] 1. Arquitectura de microservicios REST
- [x] 2. AuthService con JWT
- [x] 3. AccountService (balance, depósitos, retiros, transferencias)
- [x] 4. Almacenamiento en Google Cloud (PostgreSQL + Pub/Sub)
- [x] 5. TransactionService consumiendo Pub/Sub
- [x] 6. AuditService escuchando Pub/Sub
- [x] 7. Réplicas de AccountService y TransactionService
- [x] 8. Programa simulador de clientes (n, h, p, t)
- [x] 9. Monitor de CPU con Lanterna
- [x] 10. Interfaz web de usuario
- [x] 11. Interfaz web de administrador con gráficos

## Inicio Rápido

### 1. Prerequisitos

```bash
# Java 17, Maven, PostgreSQL, Google Cloud SDK
```

### 2. Configurar GCP

```bash
# Cloud SQL Proxy
./cloud-sql-proxy --port 5432 sistemafinancierodistribuido:us-central1:sfd-db

# Crear tópicos Pub/Sub
gcloud pubsub topics create tx-events tx-confirmed
gcloud pubsub subscriptions create tx-events-sub --topic=tx-events
gcloud pubsub subscriptions create tx-confirmed-audit-sub --topic=tx-confirmed
```

### 3. Compilar Todo

```bash
cd /ruta/a/PF_SD

# Compilar cada servicio
for dir in auth-service account-service audit-service web-interface client-simulator cpu-monitor; do
    cd $dir && mvn clean package -DskipTests && cd ..
done

# Servicios adicionales
cd transaction-service && mvn clean package -DskipTests && cd ..
cd report-service/report-service && mvn clean package -DskipTests && cd ../..
```

### 4. Ejecutar Servicios (en terminales separadas)

```bash
# Terminal 1: Cloud SQL Proxy
./cloud-sql-proxy --port 5432 sistemafinancierodistribuido:us-central1:sfd-db

# Terminal 2: AuthService
cd auth-service && java -jar target/auth-service-*.jar

# Terminal 3: AccountService
cd account-service && java -jar target/account-service-*.jar

# Terminal 4: TransactionService
cd transaction-service && java -jar target/transaction-service-*.jar

# Terminal 5: AuditService
cd audit-service && java -jar target/audit-service-*.jar

# Terminal 6: ReportService
cd report-service/report-service && java -jar target/report-service-*.jar

# Terminal 7: WebInterface
cd web-interface && java -jar target/web-interface-*.jar
```

### 5. Acceder a las Interfaces Web

- **Usuario:** http://localhost:8085/index.html
- **Administrador:** http://localhost:8085/admin.html

**Nota (GCP):** en `web-interface` puedes configurar las URLs de los APIs con
`APP_ACCOUNT_BASE_URL`, `APP_AUTH_BASE_URL` y `APP_REPORT_BASE_URL`. En `auth-service`,
usa `APP_CORS_ALLOWED_ORIGINS` con la URL pública del frontend.

### 6. Ejecutar Simulador

```bash
cd client-simulator
java -jar target/client-simulator-1.0.0.jar 10 4 1000 30
# 10 clientes, 4 hilos, $1000 iniciales, 30 tx/min
```

### 7. Ejecutar Monitor de CPU

```bash
cd cpu-monitor
java -jar target/cpu-monitor-1.0.0.jar 5
# Actualizar cada 5 segundos
```

## Estructura del Proyecto

```
PF_SD/
├── INSTRUCCIONES.md              # Documentación completa
├── README.md                     # Este archivo
├── schema.sql                    # Esquema de base de datos
├── auth-service/                 # Autenticación JWT
├── account-service/              # Gestión de cuentas
├── audit-service/                # Auditoría
├── transaction-service/          # Procesamiento de transacciones
├── web-interface/                # Interfaces web (usuario + admin)
│   └── src/main/resources/static/
│       ├── index.html           # Interfaz usuario
│       ├── admin.html           # Panel administrador
│       ├── css/                 # Estilos
│       └── js/                  # Lógica frontend
├── report-service/
│   ├── report-service/          # APIs de reportes
│   └── transaction-service/     # Copia legacy (no usar)
├── client-simulator/            # Simulador de clientes
└── cpu-monitor/                 # Monitor TUI con Lanterna
```

## Características Principales

### Seguridad
- Autenticación JWT
- Validación de CURP (18 caracteres)
- Tokens firmados digitalmente

### Arquitectura Distribuida
- Microservicios independientes
- Comunicación asíncrona vía Pub/Sub
- Procesamiento event-driven
- Réplicas para alta disponibilidad

### Consistencia
- Transacciones ACID en PostgreSQL
- Idempotencia en procesamiento de eventos
- Auditoría completa de operaciones
- Saldo total del sistema siempre consistente

### Interfaces Web Modernas
- **Usuario:** Dashboard intuitivo, operaciones en tiempo real, historial de transacciones
- **Administrador:** Gráficos interactivos (Chart.js), estadísticas en tiempo real, gestión de usuarios

### Monitoreo
- Monitor de CPU con Lanterna (TUI)
- Métricas por servicio
- Visualización en tiempo real

## Pruebas

### Prueba de Tolerancia a Fallos

```bash
# 1. Iniciar simulador de clientes
# 2. Detener TransactionService (Ctrl+C)
# 3. Mensajes quedan en Pub/Sub
# 4. Reiniciar TransactionService
# 5. Verificar que procesa mensajes pendientes
```

### Verificar Consistencia

```bash
# Monitorear saldo total (debe mantenerse constante)
watch -n 5 "psql 'host=127.0.0.1 port=5432 dbname=sfd user=app_user' \
  -c 'SELECT SUM(saldo_banco + saldo_billetera) FROM cuentas;'"
```

## Tecnologías Utilizadas

- **Backend:** Java 17, Spring Boot 3.2.6
- **Base de Datos:** PostgreSQL 17 (Google Cloud SQL)
- **Mensajería:** Google Cloud Pub/Sub
- **Frontend:** HTML5, CSS3, JavaScript vanilla, Chart.js
- **Monitoreo:** Lanterna (TUI), OSHI (métricas sistema)
- **Seguridad:** JWT (jjwt), BCrypt
- **Build:** Maven

## URLs Principales

| Servicio | URL | Puerto |
|----------|-----|--------|
| AuthService | http://localhost:8081 | 8081 |
| AccountService | http://localhost:8080 | 8080 |
| ReportService | http://localhost:8084 | 8084 |
| Interfaz Usuario | http://localhost:8085/index.html | 8085 |
| Panel Admin | http://localhost:8085/admin.html | 8085 |

## Documentación Completa

Para instrucciones detalladas de instalación, configuración, y despliegue, consultar:

**[INSTRUCCIONES.md](INSTRUCCIONES.md)**

## Autor

Proyecto Final - Sistemas Distribuidos
M. en C. Ukranio Coronilla
