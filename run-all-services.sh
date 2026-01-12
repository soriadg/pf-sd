#!/bin/bash

# Script para iniciar todos los servicios en segundo plano
# Uso: ./run-all-services.sh

BASE_DIR=$(pwd)
LOGS_DIR="$BASE_DIR/logs"

# Crear directorio de logs
mkdir -p "$LOGS_DIR"

echo "=============================================="
echo "  INICIANDO TODOS LOS SERVICIOS"
echo "=============================================="
echo ""

# Función para iniciar un servicio
start_service() {
    local name=$1
    local dir=$2
    local jar=$3
    local port=$4

    echo ">>> Iniciando $name (puerto $port)..."

    cd "$dir"
    nohup java -jar "$jar" > "$LOGS_DIR/$name.log" 2>&1 &
    local pid=$!
    echo $pid > "$LOGS_DIR/$name.pid"

    echo "    PID: $pid"
    echo "    Log: $LOGS_DIR/$name.log"

    cd "$BASE_DIR"
}

# IMPORTANTE: Verificar que Cloud SQL Proxy esté corriendo
if ! lsof -i :5433 > /dev/null 2>&1; then
    echo "⚠️  ADVERTENCIA: Cloud SQL Proxy NO está corriendo en puerto 5433"
    echo ""
    echo "Para iniciar Cloud SQL Proxy, ejecuta en otra terminal:"
    echo "  ./cloud-sql-proxy --port 5433 sistemafinancierodistribuido:us-central1:sfd-db"
    echo ""
    read -p "¿Continuar de todos modos? (y/n): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Iniciar servicios
start_service "auth-service" \
    "$BASE_DIR/auth-service" \
    "target/auth-service-0.0.1-SNAPSHOT.jar" \
    8081

sleep 2

start_service "account-service" \
    "$BASE_DIR/account-service" \
    "target/account-service-0.0.1-SNAPSHOT.jar" \
    8080

sleep 2

start_service "transaction-service" \
    "$BASE_DIR/transaction-service" \
    "target/transaction-service-0.0.1-SNAPSHOT.jar" \
    8083

sleep 2

start_service "audit-service" \
    "$BASE_DIR/audit-service" \
    "target/audit-service-0.0.1-SNAPSHOT.jar" \
    "N/A"

sleep 2

start_service "report-service" \
    "$BASE_DIR/report-service" \
    "target/report-service-0.0.1-SNAPSHOT.jar" \
    8084

sleep 2

start_service "web-interface" \
    "$BASE_DIR/web-interface" \
    "target/web-interface-0.0.1-SNAPSHOT.jar" \
    8085

echo ""
echo "=============================================="
echo "  ESPERANDO A QUE LOS SERVICIOS INICIEN..."
echo "=============================================="
echo ""

sleep 10

echo "Verificando servicios..."
echo ""

for port in 8080 8081 8084 8085; do
    echo -n "Puerto $port: "
    if lsof -i :$port > /dev/null 2>&1; then
        echo "✓ ACTIVO"
    else
        echo "✗ NO RESPONDE (revisa logs/$port.log)"
    fi
done

echo ""
echo "=============================================="
echo "  SERVICIOS INICIADOS"
echo "=============================================="
echo ""
echo "URLs disponibles:"
echo "  - Interfaz Usuario: http://localhost:8085/index.html"
echo "  - Panel Admin:      http://localhost:8085/admin.html"
echo ""
echo "Ver logs en tiempo real:"
echo "  tail -f logs/web-interface.log"
echo "  tail -f logs/auth-service.log"
echo "  tail -f logs/account-service.log"
echo ""
echo "Para detener todos los servicios:"
echo "  ./stop-all-services.sh"
echo ""
