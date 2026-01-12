#!/bin/bash

# Script para verificar que todos los servicios están funcionando
# Uso: ./check-services.sh

echo "=============================================="
echo "  VERIFICACIÓN DE SERVICIOS"
echo "=============================================="
echo ""

AUTH_PORT="${AUTH_PORT:-8081}"
ACCOUNT_PORT="${ACCOUNT_PORT:-8080}"
REPORT_PORT="${REPORT_PORT:-8084}"
WEB_PORT="${WEB_PORT:-8085}"
PROXY_PORT="${PROXY_PORT:-5432}"
TRANSACTION_PORT="${TRANSACTION_PORT:-8083}"

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
check_service "AuthService" "http://localhost:${AUTH_PORT}/auth/verify" "$AUTH_PORT"
check_service "AccountService" "http://localhost:${ACCOUNT_PORT}/account/balance" "$ACCOUNT_PORT"
check_service "ReportService" "http://localhost:${REPORT_PORT}/reports/health" "$REPORT_PORT"
check_service "WebInterface" "http://localhost:${WEB_PORT}/" "$WEB_PORT"

echo ""
echo "Nota: TransactionService y AuditService no exponen HTTP"
echo "      (solo consumen de Pub/Sub)"
echo ""

# Verificar puertos en uso
echo "=============================================="
echo "  PUERTOS EN USO"
echo "=============================================="
echo ""

for port in "$PROXY_PORT" "$ACCOUNT_PORT" "$AUTH_PORT" "$TRANSACTION_PORT" "$REPORT_PORT" "$WEB_PORT"; do
    echo -n "Puerto $port: "
    if lsof -i :$port > /dev/null 2>&1; then
        echo "✓ EN USO"
    else
        echo "✗ LIBRE"
    fi
done

echo ""
echo "URLs de las interfaces:"
echo "  - Usuario: http://localhost:${WEB_PORT}/index.html"
echo "  - Admin: http://localhost:${WEB_PORT}/admin.html"
echo ""
