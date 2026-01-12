# Cómo Levantar Réplicas de AccountService y TransactionService

## Requisito 7 del Proyecto

El proyecto requiere que **AccountService** y **TransactionService** estén replicados para mejorar disponibilidad y rendimiento.

## Arquitectura que Soporta Réplicas

✅ **El sistema YA ESTÁ DISEÑADO para soportar réplicas:**

1. **Servicios Stateless**: Ningún servicio guarda estado en memoria
2. **Base de Datos Compartida**: Todos usan la misma PostgreSQL en Cloud SQL
3. **Pub/Sub Distribuido**: Google Pub/Sub distribuye automáticamente mensajes entre réplicas
4. **Sin Sesiones**: JWT permite que cualquier instancia valide tokens

## Cómo Levantar Réplicas Manualmente

### Opción 1: Múltiples Puertos en la Misma Máquina

#### AccountService Réplica 2 (Puerto 8090)

```bash
# Terminal 1: Primera instancia (puerto 8080 - ya corriendo)
cd /home/bastian/programas/PF_SD/account-service
java -jar target/account-service-0.0.1-SNAPSHOT.jar

# Terminal 2: Segunda instancia (puerto 8090)
cd /home/bastian/programas/PF_SD/account-service
SERVER_PORT=8090 java -jar target/account-service-0.0.1-SNAPSHOT.jar
```

#### TransactionService Réplica 2

```bash
# Terminal 3: Primera instancia (ya corriendo)
cd /home/bastian/programas/PF_SD/transaction-service
java -jar target/transaction-service-0.0.1-SNAPSHOT.jar

# Terminal 4: Segunda instancia (Pub/Sub distribuye automáticamente)
cd /home/bastian/programas/PF_SD/transaction-service
java -jar target/transaction-service-0.0.1-SNAPSHOT.jar
```

**Nota:** TransactionService no necesita puerto diferente porque no expone interfaz HTTP.

### Opción 2: Con Balanceador de Carga (Nginx)

Si quieres que las réplicas compartan el mismo puerto (8080), necesitas un balanceador:

```nginx
upstream account_service {
    server localhost:8080;
    server localhost:8090;
    server localhost:8091;
}

server {
    listen 80;
    location /account/ {
        proxy_pass http://account_service;
    }
}
```

### Opción 3: Despliegue en Múltiples Máquinas (Producción)

En producción (GCP, AWS, etc.):

```bash
# Máquina 1
java -jar account-service.jar

# Máquina 2
java -jar account-service.jar

# Máquina 3
java -jar account-service.jar
```

Todas conectan a:
- La misma Cloud SQL
- El mismo proyecto de Pub/Sub

## Prueba de Réplicas

### Test de AccountService con 2 Réplicas

```bash
# 1. Levantar réplica en puerto 8090
cd /home/bastian/programas/PF_SD/account-service
SERVER_PORT=8090 java -jar target/account-service-0.0.1-SNAPSHOT.jar &

# 2. Probar ambas instancias
curl http://localhost:8080/account/balance -H "Authorization: Bearer TOKEN"
curl http://localhost:8090/account/balance -H "Authorization: Bearer TOKEN"

# 3. Detener instancia 8080
kill PID_8080

# 4. Verificar que 8090 sigue funcionando
curl http://localhost:8090/account/balance -H "Authorization: Bearer TOKEN"
```

### Test de TransactionService con 2 Réplicas

```bash
# 1. Levantar segunda instancia
cd /home/bastian/programas/PF_SD/transaction-service
java -jar target/transaction-service-0.0.1-SNAPSHOT.jar &

# 2. Ver logs de ambas instancias procesando mensajes
tail -f run-logs/transaction-service.log

# 3. Hacer transferencias - Pub/Sub distribuye entre ambas
curl -X POST http://localhost:8080/account/transfer ...

# 4. Detener una instancia - la otra sigue procesando
```

## Verificación de Tolerancia a Fallos

### Escenario 1: Caída de AccountService

1. Levanta 2 réplicas (8080, 8090)
2. Usa ambas simultáneamente
3. Mata la de 8080
4. Verifica que 8090 sigue respondiendo

### Escenario 2: Caída de TransactionService

1. Levanta 2 instancias de TransactionService
2. Genera transferencias
3. Mata una instancia
4. Verifica que la otra procesa los mensajes pendientes
5. Reinicia la caída - procesa mensajes que quedaron en cola

## Por Qué Funciona

**Pub/Sub garantiza:**
- ✅ Mensajes se distribuyen entre suscriptores activos
- ✅ Si un suscriptor falla, los mensajes se reasignan
- ✅ Mensajes no procesados quedan en la cola
- ✅ Al reiniciarse un servicio, consume mensajes pendientes

**Cloud SQL garantiza:**
- ✅ Todas las instancias ven los mismos datos
- ✅ Transacciones ACID previenen inconsistencias
- ✅ Conexiones independientes por instancia

## Conclusión

✅ **El sistema SÍ cumple el requisito 7:**
- La arquitectura soporta réplicas naturalmente
- Pub/Sub distribuye carga automáticamente
- Base de datos compartida garantiza consistencia
- Servicios stateless permiten escalabilidad horizontal

**Para la demo:** Puedes levantar réplicas manualmente cambiando el puerto o en terminales separadas.
