# 🧪 GUÍA DE PRUEBAS MANUALES - PROYECTO FINAL
## Sistema Financiero de Dinero Electrónico

**Fecha:** Lunes 12 de enero, 2026 - 8:00 AM  
**Requisitos:** Todas las pruebas del PDF del profesor

---

## 📋 PREREQUISITOS

### 1. Levantar Todos los Servicios

```bash
cd /home/bastian/programas/PF_SD
./run-services.sh start
```

### 2. Verificar que Todo Esté Corriendo

```bash
./run-services.sh status
```

**Debes ver:**
```
✅ cloud-sql-proxy: running
✅ auth-service: running  
✅ account-service: running
✅ transaction-service: running
✅ audit-service: running
✅ report-service: running
✅ web-interface: running
```

### 3. Credenciales de Administrador

```
CURP: ADMINCURP000000001
Contraseña: admin123
```

---

## 🧪 PRUEBA 1: Operaciones Básicas en Interfaz Web

**Objetivo:** Crear cuenta, hacer depósitos, transferencias, retiros y comprobar que los saldos se actualizan en el administrador.

### Paso 1.1: Abrir Interfaz de Usuario

1. Abre tu navegador
2. Ve a: **http://localhost:8085/index.html**

### Paso 1.2: Crear Nueva Cuenta

1. Click en "Registrarse"
2. Ingresa datos:
   - **CURP:** `DEMO123456HDFLRS01` (18 caracteres)
   - **Contraseña:** `demo123`
   - **Confirmar:** `demo123`
3. Click "Registrar"
4. **✅ Verificar:** Mensaje "Usuario registrado correctamente"

### Paso 1.3: Iniciar Sesión

1. En la sección de Login:
   - **CURP:** `DEMO123456HDFLRS01`
   - **Contraseña:** `demo123`
2. Click "Iniciar Sesión"
3. **✅ Verificar:** Dashboard aparece con saldos en $0.00

### Paso 1.4: Hacer un Depósito

1. En la sección "Depositar"
2. Ingresa monto: `10000`
3. Click "Depositar"
4. **✅ Verificar:**
   - Mensaje de éxito
   - Saldo Billetera cambia a $10,000.00
   - Saldo Total = $10,000.00

### Paso 1.5: Hacer un Retiro

1. En la sección "Retirar"
2. Ingresa monto: `2000`
3. Click "Retirar"
4. **✅ Verificar:**
   - Mensaje de éxito
   - Saldo Billetera = $8,000.00
   - Saldo Banco = $2,000.00
   - Saldo Total = $10,000.00

### Paso 1.6: Hacer una Transferencia

1. En la sección "Transferir"
2. Ingresa:
   - **Destinatario:** `ADMINCURP000000001`
   - **Monto:** `1000`
3. Click "Transferir"
4. **✅ Verificar:**
   - Mensaje "Transferencia enviada"
   - Estado: PENDIENTE
5. **Espera 5 segundos** para que TransactionService procese
6. Refresca la página
7. **✅ Verificar:**
   - Saldo Billetera = $7,000.00
   - Historial muestra la transferencia como CONFIRMADA

### Paso 1.7: Verificar en Panel de Administrador

1. Abre nueva pestaña: **http://localhost:8085/admin.html**
2. Login con:
   - **CURP:** `ADMINCURP000000001`
   - **Contraseña:** `admin123`
3. **✅ Verificar:**
   - Usuario `DEMO123456HDFLRS01` aparece en la tabla
   - Saldo correcto: $7,000.00 billetera + $2,000.00 banco
   - Transacciones del usuario aparecen en el historial
   - Gráficos se actualizan

---

## 🧪 PRUEBA 2: Simulador + Consistencia de Saldo

**Objetivo:** Ejecutar simulador de n clientes y verificar que el saldo total se mantiene consistente.

### Paso 2.1: Anotar Saldo Total Inicial

1. En el panel de administrador (http://localhost:8085/admin.html)
2. Anota el **"Monto Total en Sistema"**
   - Ejemplo: `$124,305.17`

### Paso 2.2: Ejecutar Simulador

**Parámetros recomendados para la demo:**
```bash
cd /home/bastian/programas/PF_SD/client-simulator

# n=5 clientes, h=3 hilos, p=2000 pesos, t=10 tx/min
java -jar target/client-simulator-1.0.0.jar 5 3 2000 10
```

**El simulador:**
- Crea 5 clientes nuevos
- Cada uno deposita $2000 iniciales
- Realiza ~10 transacciones por minuto
- **Corre indefinidamente** (presiona `Ctrl+C` para detenerlo)

### Paso 2.3: Ejecutar por 30-60 Segundos

1. Deja correr el simulador 30-60 segundos
2. Observa las estadísticas en consola
3. Presiona `Ctrl+C` para detener

### Paso 2.4: Verificar Consistencia

1. Espera 10 segundos para que se procesen transacciones pendientes
2. Refresca el panel de administrador
3. **✅ Verificar:**
   - **Usuarios nuevos:** +5 usuarios
   - **Saldo total aumentó:** +$10,000 aproximadamente (5 clientes × $2000)
   - **NO hay pérdida de dinero**
   - **NO hay generación espontánea de dinero**

**Fórmula de verificación:**
```
Saldo Final = Saldo Inicial + (5 clientes × $2000 depositados)
```

### Paso 2.5: Repetir Prueba 1 Simultáneamente (Opcional)

Mientras el simulador corre:
1. Abre http://localhost:8085/index.html
2. Haz login con el usuario DEMO
3. Realiza depósitos y retiros
4. **✅ Verificar:** Todo funciona correctamente incluso bajo carga

---

## 🧪 PRUEBA 3: Tolerancia a Fallos - TransactionService

**Objetivo:** Simular caída de TransactionService y verificar que procesa mensajes pendientes al reiniciarse.

### Escenario A: Una Réplica Cae, Otra Sigue

#### Paso 3A.1: Levantar Segunda Réplica

```bash
# Terminal nueva
cd /home/bastian/programas/PF_SD/transaction-service
java -jar target/transaction-service-0.0.1-SNAPSHOT.jar
```

#### Paso 3A.2: Verificar Ambas Réplicas Procesan

1. Haz varias transferencias desde http://localhost:8085/index.html
2. Observa logs de ambas terminales
3. **✅ Verificar:** Ambas procesan mensajes

#### Paso 3A.3: Detener Primera Réplica

```bash
# En la terminal del primer TransactionService
Ctrl+C
```

#### Paso 3A.4: Verificar que Sigue Funcionando

1. Haz más transferencias
2. **✅ Verificar:** La segunda réplica sigue procesando
3. **✅ Verificar:** Transferencias se confirman correctamente

### Escenario B: Todas las Réplicas Caen con Mensajes Pendientes

#### Paso 3B.1: Detener TODAS las instancias de TransactionService

```bash
./run-services.sh stop
# O buscar PIDs: ps aux | grep transaction-service
kill -9 [PID]
```

#### Paso 3B.2: Generar Transferencias

1. Haz 3-5 transferencias desde la interfaz web
2. **✅ Verificar:** Estado queda en "PENDIENTE"
3. **✅ Verificar:** Saldos AÚN NO se actualizan

#### Paso 3B.3: Reiniciar TransactionService

```bash
./run-services.sh start
# O manualmente:
cd /home/bastian/programas/PF_SD/transaction-service
java -jar target/transaction-service-0.0.1-SNAPSHOT.jar
```

#### Paso 3B.4: Verificar Procesamiento de Mensajes Pendientes

1. Espera 5-10 segundos
2. Refresca la página de usuario
3. **✅ Verificar:**
   - Transacciones cambian a "CONFIRMADA"
   - Saldos se actualizan correctamente
4. Revisa logs:
```bash
tail -f run-logs/transaction-service.log
```
5. **✅ Verificar:** Procesa los mensajes que estaban en cola

---

## 🧪 PRUEBA 4: Tolerancia a Fallos - AccountService

**Objetivo:** Simular caída de AccountService y verificar alta disponibilidad.

### Escenario A: Réplica con Balanceo Manual

#### Paso 4A.1: Levantar Segunda Réplica

```bash
# Terminal nueva
cd /home/bastian/programas/PF_SD/account-service
SERVER_PORT=8090 java -jar target/account-service-0.0.1-SNAPSHOT.jar
```

#### Paso 4A.2: Probar Ambas Instancias

**Prueba instancia 8080:**
```bash
TOKEN="TU_TOKEN_JWT"
curl http://localhost:8080/account/balance \
  -H "Authorization: Bearer $TOKEN"
```

**Prueba instancia 8090:**
```bash
curl http://localhost:8090/account/balance \
  -H "Authorization: Bearer $TOKEN"
```

**✅ Verificar:** Ambas retornan los mismos datos

#### Paso 4A.3: Detener Instancia 8080

```bash
# Encontrar PID
ps aux | grep account-service
kill -9 [PID_de_8080]
```

#### Paso 4A.4: Verificar Instancia 8090 Funciona

```bash
curl http://localhost:8090/account/balance \
  -H "Authorization: Bearer $TOKEN"
```

**✅ Verificar:** Sigue respondiendo correctamente

### Escenario B: Caída Total y Recuperación

#### Paso 4B.1: Detener TODAS las instancias

```bash
./run-services.sh stop
# O:
ps aux | grep account-service | awk '{print $2}' | xargs kill -9
```

#### Paso 4B.2: Intentar Operación

1. Intenta hacer login en http://localhost:8085/index.html
2. **✅ Verificar:** Falla (esperado)

#### Paso 4B.3: Reiniciar AccountService

```bash
./run-services.sh start
```

#### Paso 4B.4: Verificar Recuperación

1. Haz login nuevamente
2. **✅ Verificar:** Todo funciona normalmente
3. **✅ Verificar:** Datos preservados (no se perdió nada)

---

## 🧪 PRUEBAS ADICIONALES

### Prueba de Autenticación JWT

1. Haz login y copia el token del navegador (DevTools > Network)
2. Usa curl para hacer peticiones:
```bash
TOKEN="eyJhbGci..."
curl http://localhost:8080/account/balance \
  -H "Authorization: Bearer $TOKEN"
```
3. **✅ Verificar:** Cualquier servicio acepta el mismo token

### Prueba de Auditoría

1. Haz varias transacciones
2. Revisa los logs de AuditService:
```bash
tail -f run-logs/audit-service.log
```
3. **✅ Verificar:** Registra cada transacción confirmada

### Prueba del Monitor de CPU

```bash
cd /home/bastian/programas/PF_SD/cpu-monitor

# Actualizar cada 3 segundos
java -jar target/cpu-monitor-1.0.0.jar 3
```

**✅ Verificar:**
- Muestra todos los servicios
- Actualiza uso de CPU
- Muestra IP:Puerto de cada uno

---

## 📊 CHECKLIST FINAL ANTES DE LA DEMO

```
□ Todos los servicios corriendo (./run-services.sh status)
□ Cloud SQL Proxy conectado
□ Interfaces web accesibles (8085)
□ Panel admin funcional con gráficos
□ Usuario de prueba creado
□ Simulador compilado y probado
□ Monitor de CPU funcional
□ Conoces credenciales de admin
□ Sabes cómo detener/reiniciar servicios
□ Log files accesibles en run-logs/
```

---

## 🎯 RESUMEN DE URLs

| Componente | URL |
|------------|-----|
| Interfaz Usuario | http://localhost:8085/index.html |
| Panel Admin | http://localhost:8085/admin.html |
| AuthService API | http://localhost:8081 |
| AccountService API | http://localhost:8080 |
| ReportService API | http://localhost:8084 |

---

## 🐛 SOLUCIÓN DE PROBLEMAS COMUNES

### "Port already in use"
```bash
./run-services.sh stop
sleep 3
./run-services.sh start
```

### "Connection refused" a base de datos
```bash
# Verificar Cloud SQL Proxy
ps aux | grep cloud-sql-proxy
# Si no está corriendo:
./cloud-sql-proxy --port 5432 sistemafinancierodistribuido:us-central1:sfd-postgres
```

### Servicios no inician
```bash
# Ver logs específicos
tail -50 run-logs/[servicio].log
```

### Token JWT expirado
- Los tokens duran 1 hora
- Haz login nuevamente para obtener uno nuevo

---

## ✅ CRITERIOS DE ÉXITO

**Prueba 1:**
- ✅ Crear cuenta, depositar, retirar, transferir
- ✅ Saldos correctos en interfaz y admin

**Prueba 2:**
- ✅ Simulador crea n clientes
- ✅ Saldo total consistente

**Prueba 3:**
- ✅ TransactionService procesa después de caída
- ✅ Mensajes pendientes se procesan

**Prueba 4:**
- ✅ AccountService tolera caídas con réplicas
- ✅ Datos preservados

---

¡Listo para la evaluación del 12 de enero! 🚀
