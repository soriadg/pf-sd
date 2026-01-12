#!/bin/bash

# Script para detener todos los servicios
# Uso: ./stop-all-services.sh

BASE_DIR=$(pwd)
LOGS_DIR="$BASE_DIR/logs"

echo "=============================================="
echo "  DETENIENDO TODOS LOS SERVICIOS"
echo "=============================================="
echo ""

# Detener servicios por PID
for pidfile in "$LOGS_DIR"/*.pid; do
    if [ -f "$pidfile" ]; then
        service_name=$(basename "$pidfile" .pid)
        pid=$(cat "$pidfile")

        if ps -p $pid > /dev/null 2>&1; then
            echo ">>> Deteniendo $service_name (PID: $pid)..."
            kill $pid
            sleep 1

            # Forzar si no se detuvo
            if ps -p $pid > /dev/null 2>&1; then
                echo "    Forzando cierre..."
                kill -9 $pid
            fi

            rm "$pidfile"
            echo "    ✓ Detenido"
        else
            echo ">>> $service_name ya no está corriendo (PID: $pid)"
            rm "$pidfile"
        fi
    fi
done

echo ""
echo "=============================================="
echo "  VERIFICANDO PUERTOS"
echo "=============================================="
echo ""

for port in 8080 8081 8083 8084 8085; do
    echo -n "Puerto $port: "
    if lsof -i :$port > /dev/null 2>&1; then
        echo "⚠️  TODAVÍA EN USO"
    else
        echo "✓ LIBRE"
    fi
done

echo ""
echo "Todos los servicios han sido detenidos."
echo ""
