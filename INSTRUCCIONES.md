# Sistema Financiero de Dinero Electrónico - Instrucciones de Ejecución

## Descripción del Proyecto

Sistema distribuido de dinero electrónico basado en microservicios REST con arquitectura event-driven utilizando Google Cloud Pub/Sub. Permite a los usuarios depositar dinero desde su cuenta bancaria para convertirlo en dinero electrónico, realizar transferencias, pagos y recibir dinero de otros usuarios.

## Arquitectura del Sistema

### Microservicios

1. **AuthService** (Puerto 8081)
   - Autenticación de usuarios con JWT
   - Registro de usuarios (CURP + contraseña)
   - Validación de tokens

2. **AccountService** (Puerto 8080)
   - Gestión de cuentas de usuario
   - Balance de cuenta (banco + billetera)
   - Depósitos síncronos (banco → billetera)
   - Retiros síncronos (billetera → banco)
   - Transferencias asíncronas vía Pub/Sub

3. **TransactionService** (Puerto 8083)
   - Procesamiento asíncrono de transacciones
   - Consumidor de eventos Pub/Sub
   - Actualización de saldos
   - Publicación de confirmaciones

4. **AuditService**
   - Auditoría de transacciones confirmadas
   - Almacenamiento en BD y GCS

5. **ReportService** (Puerto 8084)
   - APIs de reportes para el administrador
   - Estadísticas del sistema
   - Datos para gráficos

6. **WebInterface** (Puerto 8085)
   - Interfaz web para usuarios
   - Panel de administración
   - Visualización de transacciones y estadísticas

### Componentes Adicionales

7. **ClientSimulator**
   - Simulador de n clientes con h hilos
   - Generación de tráfico de transacciones

8. **CPUMonitor**
   - Monitor TUI con Lanterna
   - Visualización de uso de CPU por servicio

## Requisitos Previos

### Software Necesario

1. **Java 17** o superior
2. **Maven 3.8+**
3. **PostgreSQL** (Cloud SQL en GCP o local)
4. **Google Cloud SDK** (para Pub/Sub y Cloud SQL Proxy)

### Configuración de Google Cloud Platform

#### 1. Crear Proyecto en GCP

```bash
gcloud projects create sistemafinancierodistribuido
gcloud config set project sistemafinancierodistribuido
```

#### 2. Habilitar APIs necesarias

```bash
gcloud services enable sqladmin.googleapis.com
gcloud services enable pubsub.googleapis.com
```

#### 3. Configurar Cloud SQL (PostgreSQL)

```bash
# Crear instancia de PostgreSQL
gcloud sql instances create sfd-db \
  --database-version=POSTGRES_17 \
  --tier=db-f1-micro \
  --region=us-central1

# Crear base de datos
gcloud sql databases create sfd --instance=sfd-db

# Crear usuario
gcloud sql users create app_user \
  --instance=sfd-db \
  --password=123456789
```

#### 4. Configurar Cloud SQL Proxy

```bash
# Descargar Cloud SQL Proxy
curl -o cloud-sql-proxy https://storage.googleapis.com/cloud-sql-connectors/cloud-sql-proxy/v2.8.0/cloud-sql-proxy.linux.amd64
chmod +x cloud-sql-proxy

# Ejecutar proxy (en terminal separada)
./cloud-sql-proxy --port 5433 sistemafinancierodistribuido:us-central1:sfd-db
```

#### 5. Inicializar Base de Datos

```bash
# Conectarse a la base de datos a través del proxy
psql "host=127.0.0.1 port=5433 sslmode=disable dbname=sfd user=app_user"

# Ejecutar el script schema.sql
\i /ruta/a/PFinal/schema.sql
```

#### 6. Configurar Google Cloud Pub/Sub

```bash
# Crear tópicos
gcloud pubsub topics create tx-events
gcloud pubsub topics create tx-confirmed

# Crear suscripciones
gcloud pubsub subscriptions create tx-events-sub --topic=tx-events
gcloud pubsub subscriptions create tx-confirmed-audit-sub --topic=tx-confirmed

# Configurar credenciales
gcloud auth application-default login
```

## Compilación del Proyecto

### 1. Compilar todos los microservicios

```bash
cd /home/soriadg/Documentos/PFsistemasDistribuidos/PFinal

# AuthService
cd auth-service
mvn clean package -DskipTests
cd ..

# AccountService
cd account-service
mvn clean package -DskipTests
cd ..

# TransactionService (en New folder)
cd "New folder/transaction-service"
mvn clean package -DskipTests
cd ../..

# AuditService
cd audit-service
mvn clean package -DskipTests
cd ..

# ReportService (en New folder)
cd "New folder/report-service"
mvn clean package -DskipTests
cd ../..

# WebInterface
cd web-interface
mvn clean package -DskipTests
cd ..

# ClientSimulator
cd client-simulator
mvn clean package
cd ..

# CPUMonitor
cd cpu-monitor
mvn clean package
cd ..
```

## Ejecución del Sistema

### Orden de Inicio de Servicios

Es importante iniciar los servicios en el siguiente orden para evitar problemas de dependencias:

#### 1. Cloud SQL Proxy (Terminal 1)

```bash
cd /home/soriadg/Documentos/PFsistemasDistribuidos/PFinal
./cloud-sql-proxy --port 5433 sistemafinancierodistribuido:us-central1:sfd-db
```

#### 2. AuthService (Terminal 2)

```bash
cd /home/soriadg/Documentos/PFsistemasDistribuidos/PFinal/auth-service
java -jar target/auth-service-0.0.1-SNAPSHOT.jar
```

Debería iniciar en: **http://localhost:8081**

#### 3. AccountService (Terminal 3)

```bash
cd /home/soriadg/Documentos/PFsistemasDistribuidos/PFinal/account-service
java -jar target/account-service-0.0.1-SNAPSHOT.jar
```

Debería iniciar en: **http://localhost:8080**

#### 4. TransactionService (Terminal 4)

```bash
cd "/home/soriadg/Documentos/PFsistemasDistribuidos/PFinal/New folder/transaction-service"
java -jar target/transaction-service-0.0.1-SNAPSHOT.jar
```

Este servicio NO expone puerto HTTP (solo consume Pub/Sub)

#### 5. AuditService (Terminal 5)

```bash
cd /home/soriadg/Documentos/PFsistemasDistribuidos/PFinal/audit-service
java -jar target/audit-service-0.0.1-SNAPSHOT.jar
```

Este servicio NO expone puerto HTTP (solo consume Pub/Sub)

#### 6. ReportService (Terminal 6)

```bash
cd "/home/soriadg/Documentos/PFsistemasDistribuidos/PFinal/New folder/report-service"
java -jar target/report-service-0.0.1-SNAPSHOT.jar
```

Debería iniciar en: **http://localhost:8084**

#### 7. WebInterface (Terminal 7)

```bash
cd /home/soriadg/Documentos/PFsistemasDistribuidos/PFinal/web-interface
java -jar target/web-interface-0.0.1-SNAPSHOT.jar
```

Debería iniciar en: **http://localhost:8085**

### Verificar que Todos los Servicios Están Funcionando

```bash
# AuthService
curl http://localhost:8081/auth/verify

# AccountService (requiere token, pero debería responder)
curl http://localhost:8080/account/balance

# ReportService
curl http://localhost:8084/reports/health

# WebInterface
curl http://localhost:8085
```

## Uso de las Interfaces Web

### Interfaz de Usuario

**URL:** http://localhost:8085/index.html

#### Registro de Usuario

1. Abrir la interfaz en el navegador
2. Click en "Regístrate"
3. Ingresar CURP (18 caracteres alfanuméricos en mayúsculas)
   - Ejemplo: `ROSG850607HDFRNS01`
4. Ingresar contraseña
5. Click en "Registrar"

#### Iniciar Sesión

1. Ingresar CURP y contraseña
2. Click en "Ingresar"
3. Se mostrará el dashboard con:
   - Saldo en banco
   - Saldo en billetera
   - Saldo total
   - Opciones para depositar, retirar y transferir
   - Historial de transacciones

#### Realizar Transacciones

**Depositar (Banco → Billetera):**
1. Click en tab "Depositar"
2. Ingresar monto
3. Click en "Depositar"

**Retirar (Billetera → Banco):**
1. Click en tab "Retirar"
2. Ingresar monto
3. Click en "Retirar"

**Transferir:**
1. Click en tab "Transferir"
2. Ingresar CURP de destino
3. Ingresar monto
4. Click en "Transferir"

### Interfaz de Administrador

**URL:** http://localhost:8085/admin.html

#### Acceso

1. Registrar un usuario administrador (puede hacerse vía API):

```bash
curl -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "curp": "ADMIN000000000001",
    "contrasena": "admin123",
    "rol": "ADMIN"
  }'
```

2. Iniciar sesión con credenciales de administrador

#### Funcionalidades del Panel

- **Resumen:** Usuarios totales, transacciones, monto total, transacciones hoy
- **Gráficos:**
  - Número de transacciones por periodo (día/semana/mes)
  - Monto de transacciones por periodo
  - Distribución por tipo (depósitos, retiros, transferencias)
- **Tabla de Usuarios:** Balances de todos los usuarios
- **Ver Transacciones:** Click en "Ver Transacciones" para ver historial de cada usuario
- **Log Global:** Todas las transacciones del sistema

## Ejecutar Simulador de Clientes

El simulador crea n clientes que realizan transacciones continuamente con h hilos.

### Parámetros

- **n:** Número de clientes
- **h:** Número de hilos (1 < h < 9)
- **p:** Pesos iniciales que deposita cada cliente
- **t:** Transacciones por minuto por cliente (1 < t < 60)

### Ejemplo de Ejecución

```bash
cd /home/soriadg/Documentos/PFsistemasDistribuidos/PFinal/client-simulator

# Simular 10 clientes con 4 hilos, $1000 iniciales, 30 transacciones/min
java -jar target/client-simulator-1.0.0.jar 10 4 1000 30
```

El simulador:
1. Registrará automáticamente n clientes
2. Depositará p pesos en cada cuenta
3. Iniciará transacciones aleatorias (50% transferencias, 30% depósitos, 20% retiros)
4. Mostrará estadísticas cada 10 segundos
5. Ejecutará indefinidamente hasta presionar Ctrl+C

## Ejecutar Monitor de CPU

El monitor muestra el uso de CPU de todos los servicios en una interfaz TUI.

### Parámetros

- **n:** Segundos entre actualizaciones (default: 5)

### Ejemplo de Ejecución

```bash
cd /home/soriadg/Documentos/PFsistemasDistribuidos/PFinal/cpu-monitor

# Actualizar cada 3 segundos
java -jar target/cpu-monitor-1.0.0.jar 3
```

El monitor muestra:
- CPU total del sistema
- Tabla con cada servicio:
  - Nombre del servicio
  - IP:Puerto
  - % de CPU
  - Estado (Activo/Inactivo)
- Colores:
  - Verde: CPU < 50%
  - Amarillo: 50% <= CPU <= 80%
  - Rojo: CPU > 80%

Presionar 'Q' para salir.

## Pruebas del Sistema

### Prueba 1: Operaciones Básicas

```bash
# Registrar usuario
curl -X POST http://localhost:8081/auth/register \
  -H "Content-Type: application/json" \
  -d '{"curp":"PRUEBA0001TESTXX01","contrasena":"test123","rol":"USUARIO"}'

# Login
TOKEN=$(curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{"curp":"PRUEBA0001TESTXX01","contrasena":"test123"}' \
  | jq -r '.token')

# Ver balance
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/account/balance

# Depositar
curl -X POST http://localhost:8080/account/deposit \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"monto":1000}'

# Ver balance actualizado
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/account/balance
```

### Prueba 2: Tolerancia a Fallos

#### Caída de TransactionService

1. Iniciar simulador de clientes
2. Detener TransactionService (Ctrl+C en terminal 4)
3. Las transacciones quedarán en cola Pub/Sub
4. Reiniciar TransactionService
5. Verificar que procesa los mensajes pendientes

#### Caída de AccountService

1. Iniciar simulador de clientes
2. Detener AccountService (Ctrl+C en terminal 3)
3. Las peticiones HTTP fallarán
4. Reiniciar AccountService
5. El simulador continuará automáticamente

### Prueba 3: Consistencia del Sistema

```bash
# Ver monto total del sistema (debe mantenerse constante)
psql "host=127.0.0.1 port=5433 dbname=sfd user=app_user" \
  -c "SELECT SUM(saldo_banco + saldo_billetera) FROM cuentas;"

# Ejecutar durante el simulador para verificar que no cambia
watch -n 5 "psql 'host=127.0.0.1 port=5433 dbname=sfd user=app_user' \
  -c 'SELECT SUM(saldo_banco + saldo_billetera) FROM cuentas;'"
```

## Réplicas de Servicios

Para ejecutar réplicas de AccountService y TransactionService:

### Segunda instancia de AccountService (Puerto 8090)

```bash
cd /home/soriadg/Documentos/PFsistemasDistribuidos/PFinal/account-service
SERVER_PORT=8090 java -jar target/account-service-0.0.1-SNAPSHOT.jar
```

### Segunda instancia de TransactionService

```bash
cd "/home/soriadg/Documentos/PFsistemasDistribuidos/PFinal/New folder/transaction-service"
java -jar target/transaction-service-0.0.1-SNAPSHOT.jar
```

Pub/Sub distribuirá automáticamente los mensajes entre las instancias.

## Solución de Problemas

### Error: Cannot connect to database

**Problema:** Cloud SQL Proxy no está ejecutándose

**Solución:**
```bash
./cloud-sql-proxy --port 5433 sistemafinancierodistribuido:us-central1:sfd-db
```

### Error: Topic not found

**Problema:** Tópicos de Pub/Sub no existen

**Solución:**
```bash
gcloud pubsub topics create tx-events
gcloud pubsub topics create tx-confirmed
gcloud pubsub subscriptions create tx-events-sub --topic=tx-events
gcloud pubsub subscriptions create tx-confirmed-audit-sub --topic=tx-confirmed
```

### Error: 401 Unauthorized

**Problema:** Token JWT inválido o expirado

**Solución:** Hacer login nuevamente para obtener un nuevo token

### Puerto ya en uso

**Problema:** Un servicio no puede iniciar porque el puerto está ocupado

**Solución:**
```bash
# Encontrar proceso usando el puerto
lsof -i :8080

# Matar proceso
kill -9 <PID>
```

## URLs del Sistema

| Servicio | URL | Descripción |
|----------|-----|-------------|
| AuthService | http://localhost:8081 | Autenticación y registro |
| AccountService | http://localhost:8080 | Operaciones de cuenta |
| ReportService | http://localhost:8084 | Reportes y estadísticas |
| WebInterface (Usuario) | http://localhost:8085/index.html | Interfaz de usuario |
| WebInterface (Admin) | http://localhost:8085/admin.html | Panel de administración |

## Endpoints API Principales

### AuthService (8081)

- `POST /auth/register` - Registrar usuario
- `POST /auth/login` - Iniciar sesión
- `GET /auth/verify` - Verificar token

### AccountService (8080)

- `GET /account/balance` - Ver balance (requiere JWT)
- `POST /account/deposit` - Depositar (requiere JWT)
- `POST /account/withdraw` - Retirar (requiere JWT)
- `POST /account/transfer` - Transferir (requiere JWT)
- `GET /account/transactions` - Ver transacciones (requiere JWT)

### ReportService (8084)

- `GET /reports/summary` - Resumen del sistema
- `GET /reports/users` - Lista de usuarios
- `GET /reports/transactions` - Todas las transacciones
- `GET /reports/transactions/{curp}` - Transacciones de un usuario
- `GET /reports/charts/transactions` - Datos para gráfico de transacciones
- `GET /reports/charts/amounts` - Datos para gráfico de montos
- `GET /reports/charts/types` - Distribución por tipo

## Arquitectura de Archivos

```
PFinal/
├── schema.sql                      # Esquema de base de datos
├── INSTRUCCIONES.md               # Este archivo
├── auth-service/                  # Microservicio de autenticación
├── account-service/               # Microservicio de cuentas
├── audit-service/                 # Microservicio de auditoría
├── New folder/
│   ├── transaction-service/       # Procesamiento de transacciones
│   └── report-service/           # Servicio de reportes
├── web-interface/                # Interfaces web
│   └── src/main/resources/static/
│       ├── index.html            # Interfaz de usuario
│       ├── admin.html            # Panel de administración
│       ├── css/                  # Estilos
│       └── js/                   # JavaScript
├── client-simulator/             # Simulador de clientes
└── cpu-monitor/                  # Monitor de CPU

```

## Notas Finales

- Todos los servicios deben estar ejecutándose para el funcionamiento completo del sistema
- Las transacciones asíncronas (transferencias) pueden tomar unos segundos en procesarse
- El sistema mantiene consistencia eventual a través de Pub/Sub
- Los saldos totales del sistema siempre deben mantenerse constantes
- Para producción, configurar balanceador de carga para las réplicas

## Contacto y Soporte

Para problemas o preguntas sobre el sistema, revisar los logs de cada microservicio en las consolas donde están ejecutándose.
