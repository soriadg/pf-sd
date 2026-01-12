# 🎬 SCRIPT DE DEMOSTRACIÓN - TOLERANCIA A FALLOS

## PRUEBA 3: TransactionService

### Setup (antes de la demo)
```bash
# Terminal 1: Primera réplica
cd /home/bastian/programas/PF_SD/transaction-service
java -jar target/transaction-service-0.0.1-SNAPSHOT.jar

# Terminal 2: Segunda réplica
cd /home/bastian/programas/PF_SD/transaction-service
java -jar target/transaction-service-0.0.1-SNAPSHOT.jar
```

### Durante la demo
1. **Mostrar logs de ambas terminales**
   - "Profesor, aquí tengo 2 instancias de TransactionService corriendo"

2. **Hacer transferencias desde la web**
   - http://localhost:8085/index.html
   - Login y hacer 2-3 transferencias
   - "Observe que ambas instancias procesan mensajes" (señalar logs)

3. **Matar primera instancia**
   - `Ctrl+C` en Terminal 1
   - "Simulo la caída del primer servicio"

4. **Hacer más transferencias**
   - Hacer 2 transferencias más
   - "El sistema sigue funcionando con la segunda instancia"
   - Mostrar logs de Terminal 2 procesando

5. **Matar segunda instancia**
   - `Ctrl+C` en Terminal 2
   - "Ahora simulo caída total mientras hay mensajes pendientes"

6. **Generar transferencias pendientes**
   - Hacer 3 transferencias
   - Mostrar que quedan en PENDIENTE
   - "Los mensajes están en la cola Pub/Sub esperando"

7. **Reiniciar una instancia**
   - En Terminal 2: volver a ejecutar el JAR
   - "Al reiniciar, procesa automáticamente los mensajes pendientes"
   - Esperar 5-10 segundos
   - Refrescar página → estado cambia a CONFIRMADA
   - "Nada se perdió, Pub/Sub guardó todo"

### ✅ Criterios demostrados
- ✅ Alta disponibilidad con réplicas
- ✅ Distribución automática de carga (Pub/Sub)
- ✅ Tolerancia a fallos
- ✅ Procesamiento de mensajes pendientes
- ✅ Sin pérdida de datos

---

## PRUEBA 4: AccountService

### Opción A: Demostración Simplificada (Recomendada)

1. **Mostrar servicio funcionando**
   - Hacer login en http://localhost:8085/index.html
   - Hacer depósito
   - "AccountService está respondiendo correctamente"

2. **Simular caída**
   ```bash
   # Encontrar PID
   ps aux | grep account-service | grep 8080
   kill -9 [PID]
   ```
   - Intentar hacer login → falla
   - "Servicio caído, no responde"

3. **Reiniciar**
   ```bash
   cd /home/bastian/programas/PF_SD/account-service
   java -jar target/account-service-0.0.1-SNAPSHOT.jar &
   ```
   - Esperar 10 segundos
   - Hacer login → funciona
   - Ver saldo → datos preservados
   - "Datos en Cloud SQL, nada se perdió"

4. **Explicar réplicas**
   - "En producción, con balanceador de carga (Nginx o GCP Load Balancer)"
   - "Múltiples instancias en puertos diferentes"
   - "El balanceador distribuye peticiones"
   - "Si una cae, las demás siguen respondiendo"

### Opción B: Demostración con Réplica Real

1. **Levantar segunda réplica**
   ```bash
   # Terminal nueva
   cd /home/bastian/programas/PF_SD/account-service
   SERVER_PORT=8090 java -jar target/account-service-0.0.1-SNAPSHOT.jar
   ```

2. **Probar ambas con curl**
   ```bash
   # Obtener token
   TOKEN=$(curl -s -X POST http://localhost:8081/auth/login \
     -H "Content-Type: application/json" \
     -d '{"curp":"ADMINCURP000000001","contrasena":"admin123"}' | \
     python3 -c "import sys, json; print(json.load(sys.stdin)['token'])")
   
   # Probar puerto 8080
   curl http://localhost:8080/account/balance \
     -H "Authorization: Bearer $TOKEN"
   
   # Probar puerto 8090
   curl http://localhost:8090/account/balance \
     -H "Authorization: Bearer $TOKEN"
   ```
   - "Ambas instancias retornan los mismos datos"

3. **Matar instancia 8080**
   ```bash
   kill -9 [PID_8080]
   ```
   - Probar 8090 de nuevo → sigue funcionando
   - "La segunda instancia continúa operando"

### ✅ Criterios demostrados
- ✅ Servicio tolera caídas
- ✅ Datos preservados en Cloud SQL
- ✅ Recuperación automática
- ✅ Arquitectura lista para réplicas

---

## 💡 TIPS PARA LA DEMO

### Antes de empezar
- [ ] Todos los servicios corriendo (`./run-services.sh status`)
- [ ] Usuario de prueba creado
- [ ] Panel admin abierto en otra pestaña
- [ ] Terminales preparadas

### Durante la demo
- **Habla mientras ejecutas**: Explica qué haces
- **Muestra los logs**: Demuestra que los servicios procesan
- **Usa el panel admin**: Muestra que los datos se actualizan
- **Sé paciente**: Espera 5-10 segundos para que Pub/Sub procese

### Si algo falla
- **Logs son tu amigo**: `tail -f run-logs/[servicio].log`
- **Reinicia limpio**: `./run-services.sh stop && ./run-services.sh start`
- **Explica el concepto**: Aunque falle la demo técnica, explica la arquitectura

---

## 🎯 ARGUMENTOS CLAVE

**Para TransactionService:**
- "Pub/Sub distribuye mensajes automáticamente entre réplicas"
- "Si una instancia cae, Pub/Sub reasigna mensajes a las activas"
- "Los mensajes no procesados quedan en cola"
- "Al reiniciar, consume automáticamente lo pendiente"

**Para AccountService:**
- "Servicios stateless: no guardan sesión en memoria"
- "Todos consultan la misma base de datos (Cloud SQL)"
- "JWT permite que cualquier instancia valide tokens"
- "Con balanceador de carga, las réplicas comparten tráfico"

---

¡Éxito en la demo! 🚀
