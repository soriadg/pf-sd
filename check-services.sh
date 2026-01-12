#!/bin/bash

# Script para verificar que todos los servicios están funcionando
# Uso: ./check-services.sh

echo "=============================================="
echo "  VERIFICACIÓN DE SERVICIOS"
echo "=============================================="
echo ""

check_service() {
    local name=$1
    local url=$2
    local port=$3

    echo -n "Verificando $name (puerto $port)... "

    if curl -s --max-time 3 "$url" > /dev/null 2>&1; then
        echo "✓ OK"
        return 0
    else
        echo "✗ NO RESPONDE"
        return 1
    fi
}

# Verificar servicios HTTP
check_service "AuthService" "http://localhost:8081/auth/verify" "8081"
check_service "AccountService" "http://localhost:8080/account/balance" "8080"
check_service "ReportService" "http://localhost:8084/reports/health" "8084"
check_service "WebInterface" "http://localhost:8085/" "8085"

echo ""
echo "Nota: TransactionService y AuditService no exponen HTTP"
echo "      (solo consumen de Pub/Sub)"
echo ""

# Verificar puertos en uso
echo "=============================================="
echo "  PUERTOS EN USO"
echo "=============================================="
echo ""

for port in 5432 8080 8081 8083 8084 8085; do
    echo -n "Puerto $port: "
    if lsof -i :$port > /dev/null 2>&1; then
        echo "✓ EN USO"
    else
        echo "✗ LIBRE"
    fi
done

echo ""
echo "URLs de las interfaces:"
echo "  - Usuario: http://localhost:8085/index.html"
echo "  - Admin: http://localhost:8085/admin.html"
echo ""
