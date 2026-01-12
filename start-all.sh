#!/bin/bash

# Script para compilar y preparar todos los servicios
# Uso: ./start-all.sh

echo "=============================================="
echo "  COMPILACIÓN DE TODOS LOS SERVICIOS"
echo "=============================================="

BASE_DIR=$(pwd)

# Array de servicios
declare -a services=(
    "auth-service"
    "account-service"
    "audit-service"
    "web-interface"
    "client-simulator"
    "cpu-monitor"
)

declare -a new_folder_services=(
)

# Compilar servicios en carpeta principal
for service in "${services[@]}"; do
    echo ""
    echo ">>> Compilando $service..."
    cd "$BASE_DIR/$service"

    if [ "$service" == "client-simulator" ] || [ "$service" == "cpu-monitor" ]; then
        mvn clean package
    else
        mvn clean package -DskipTests
    fi

    if [ $? -eq 0 ]; then
        echo "✓ $service compilado exitosamente"
    else
        echo "✗ Error compilando $service"
        exit 1
    fi
done

# Compilar servicios adicionales en raíz
echo ""
echo ">>> Compilando transaction-service..."
cd "$BASE_DIR/transaction-service"

mvn clean package -DskipTests

if [ $? -eq 0 ]; then
    echo "✓ transaction-service compilado exitosamente"
else
    echo "✗ Error compilando transaction-service"
    exit 1
fi

echo ""
echo ">>> Compilando report-service..."
cd "$BASE_DIR/report-service/report-service"

mvn clean package -DskipTests

if [ $? -eq 0 ]; then
    echo "✓ report-service compilado exitosamente"
else
    echo "✗ Error compilando report-service"
    exit 1
fi

cd "$BASE_DIR"

echo ""
echo "=============================================="
echo "  COMPILACIÓN COMPLETADA"
echo "=============================================="
echo ""
echo "Para ejecutar los servicios, usar los siguientes comandos"
echo "en terminales separadas:"
echo ""
echo "1. Cloud SQL Proxy:"
echo "   ./cloud-sql-proxy --port 5432 sistemafinancierodistribuido:us-central1:sfd-db"
echo ""
echo "2. AuthService:"
echo "   cd auth-service && java -jar target/auth-service-0.0.1-SNAPSHOT.jar"
echo ""
echo "3. AccountService:"
echo "   cd account-service && java -jar target/account-service-0.0.1-SNAPSHOT.jar"
echo ""
echo "4. TransactionService:"
echo "   cd transaction-service && java -jar target/transaction-service-0.0.1-SNAPSHOT.jar"
echo ""
echo "5. AuditService:"
echo "   cd audit-service && java -jar target/audit-service-0.0.1-SNAPSHOT.jar"
echo ""
echo "6. ReportService:"
echo "   cd report-service/report-service && java -jar target/report-service-0.0.1-SNAPSHOT.jar"
echo ""
echo "7. WebInterface:"
echo "   cd web-interface && java -jar target/web-interface-0.0.1-SNAPSHOT.jar"
echo ""
echo "Interfaces Web:"
echo "  - Usuario: http://localhost:8085/index.html"
echo "  - Admin: http://localhost:8085/admin.html"
echo ""
echo "Herramientas:"
echo "  - Simulador: cd client-simulator && java -jar target/client-simulator-1.0.0.jar 10 4 1000 30"
echo "  - Monitor: cd cpu-monitor && java -jar target/cpu-monitor-1.0.0.jar 5"
echo ""
