// Configuration
const runtimeConfig = window.__CONFIG__ || {};
const REPORT_API = runtimeConfig.REPORT_BASE_URL || 'http://localhost:8084'; // ReportService
const AUTH_BASE_URL = runtimeConfig.AUTH_BASE_URL || 'http://localhost:8081'; // AuthService

// State
let adminToken = localStorage.getItem('adminToken');
let adminCurp = localStorage.getItem('adminCurp');
let currentTimeFilter = 'day';

const CURP_PATTERN = /^[A-Z0-9]{18}$/;

function normalizeCurp(value) {
    return (value || '').trim().toUpperCase();
}

function getCurpError(curp) {
    if (!curp) {
        return 'CURP requerida';
    }
    if (!CURP_PATTERN.test(curp)) {
        return 'CURP inválida: usa 18 caracteres en mayúsculas (A-Z/0-9)';
    }
    return null;
}

function getPasswordError(password) {
    if (!password || !password.trim()) {
        return 'Contraseña requerida';
    }
    return null;
}

async function parseJsonSafe(response) {
    const text = await response.text();
    if (!text) {
        return {};
    }
    try {
        return JSON.parse(text);
    } catch (error) {
        return { error: text };
    }
}

// Charts
let transactionsChart = null;
let amountChart = null;
let typeChart = null;

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    if (adminToken && adminCurp) {
        showAdminDashboard();
        loadAllData();
    }
});

// Auth Functions
async function adminLogin(event) {
    event.preventDefault();

    const curp = normalizeCurp(document.getElementById('admin-curp').value);
    const password = document.getElementById('admin-password').value;

    const curpError = getCurpError(curp);
    if (curpError) {
        showNotification(curpError, 'error');
        return;
    }

    const passwordError = getPasswordError(password);
    if (passwordError) {
        showNotification(passwordError, 'error');
        return;
    }

    showLoading(true);

    try {
        const response = await fetch(`${AUTH_BASE_URL}/auth/login`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                curp: curp,
                contrasena: password
            })
        });

        const data = await parseJsonSafe(response);

        if (response.ok && data.token) {
            // Verify admin role
            const verifyResponse = await fetch(`${AUTH_BASE_URL}/auth/verify`, {
                headers: {
                    'Authorization': `Bearer ${data.token}`
                }
            });

            const verifyData = await parseJsonSafe(verifyResponse);
            if (!verifyResponse.ok) {
                showNotification(verifyData.error || 'Token inválido', 'error');
                return;
            }

            if (verifyData.info?.rol !== 'ADMIN') {
                showNotification('Acceso denegado. Requiere rol de administrador', 'error');
                return;
            }

            adminToken = data.token;
            adminCurp = curp;
            localStorage.setItem('adminToken', adminToken);
            localStorage.setItem('adminCurp', adminCurp);

            showNotification('Acceso concedido', 'success');
            showAdminDashboard();
            loadAllData();
        } else {
            showNotification(data.error || `Error al iniciar sesión (${response.status})`, 'error');
        }
    } catch (error) {
        showNotification('Error de conexión: ' + error.message, 'error');
    } finally {
        showLoading(false);
    }
}

function adminLogout() {
    adminToken = null;
    adminCurp = null;
    localStorage.removeItem('adminToken');
    localStorage.removeItem('adminCurp');

    document.getElementById('admin-auth-section').style.display = 'flex';
    document.getElementById('admin-dashboard-section').style.display = 'none';

    showNotification('Sesión cerrada', 'info');
}

function showAdminDashboard() {
    document.getElementById('admin-auth-section').style.display = 'none';
    document.getElementById('admin-dashboard-section').style.display = 'block';
    document.getElementById('admin-user-curp').textContent = adminCurp;
}

// Data Loading Functions
async function loadAllData() {
    await Promise.all([
        loadSummary(),
        loadAllUsers(),
        loadAllTransactions(),
        loadCharts()
    ]);
}

async function loadSummary() {
    try {
        const response = await fetch(`${REPORT_API}/reports/summary`, {
            headers: {
                'Authorization': `Bearer ${adminToken}`
            }
        });

        if (response.ok) {
            const data = await response.json();
            document.getElementById('total-users').textContent = data.totalUsuarios || 0;
            document.getElementById('total-transactions').textContent = data.totalTransacciones || 0;
            document.getElementById('total-money').textContent = `$${formatNumber(data.montoTotal || 0)}`;
            document.getElementById('today-transactions').textContent = data.transaccionesHoy || 0;
        }
    } catch (error) {
        console.error('Error loading summary:', error);
    }
}

async function loadAllUsers() {
    try {
        const response = await fetch(`${REPORT_API}/reports/users`, {
            headers: {
                'Authorization': `Bearer ${adminToken}`
            }
        });

        if (response.ok) {
            const users = await response.json();
            displayUsers(users);
        }
    } catch (error) {
        console.error('Error loading users:', error);
    }
}

function displayUsers(users) {
    const tbody = document.getElementById('users-table-body');

    if (!users || users.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" class="no-data">No hay usuarios</td></tr>';
        return;
    }

    tbody.innerHTML = users.map(user => `
        <tr>
            <td><strong>${user.curp}</strong></td>
            <td>$${formatNumber(user.saldo_banco || 0)}</td>
            <td>$${formatNumber(user.saldo_billetera || 0)}</td>
            <td><strong>$${formatNumber((user.saldo_banco || 0) + (user.saldo_billetera || 0))}</strong></td>
            <td>
                <button class="btn btn-info" onclick="viewUserTransactions('${user.curp}')">
                    Ver Transacciones
                </button>
            </td>
        </tr>
    `).join('');
}

async function viewUserTransactions(curp) {
    try {
        const response = await fetch(`${REPORT_API}/reports/transactions/${curp}`, {
            headers: {
                'Authorization': `Bearer ${adminToken}`
            }
        });

        if (response.ok) {
            const transactions = await response.json();
            document.getElementById('selected-user-curp').textContent = curp;
            displayUserTransactions(transactions);
            document.getElementById('user-transactions-section').style.display = 'block';
            document.getElementById('user-transactions-section').scrollIntoView({ behavior: 'smooth' });
        }
    } catch (error) {
        showNotification('Error al cargar transacciones del usuario', 'error');
    }
}

function displayUserTransactions(transactions) {
    const container = document.getElementById('user-transactions-list');

    if (!transactions || transactions.length === 0) {
        container.innerHTML = '<p class="no-data">No hay transacciones para este usuario</p>';
        return;
    }

    container.innerHTML = transactions.map(tx => `
        <div class="transaction-item ${tx.tipo}">
            <div class="transaction-header">
                <span class="transaction-type">${tx.tipo}</span>
                <span class="transaction-amount">$${formatNumber(tx.monto)}</span>
            </div>
            <div class="transaction-details">Origen: ${tx.curp_origen || 'N/A'}</div>
            <div class="transaction-details">Destino: ${tx.curp_destino || 'N/A'}</div>
            <div class="transaction-details">
                <span class="transaction-status ${tx.estado}">${tx.estado}</span>
            </div>
            <div class="transaction-date">${formatDate(tx.creado_en)}</div>
        </div>
    `).join('');
}

function closeUserTransactions() {
    document.getElementById('user-transactions-section').style.display = 'none';
}

async function loadAllTransactions() {
    try {
        const response = await fetch(`${REPORT_API}/reports/transactions`, {
            headers: {
                'Authorization': `Bearer ${adminToken}`
            }
        });

        if (response.ok) {
            const transactions = await response.json();
            displayAllTransactions(transactions);
        }
    } catch (error) {
        console.error('Error loading transactions:', error);
    }
}

function displayAllTransactions(transactions) {
    const container = document.getElementById('all-transactions-list');

    if (!transactions || transactions.length === 0) {
        container.innerHTML = '<p class="no-data">No hay transacciones</p>';
        return;
    }

    // Show only last 100 transactions
    const recentTransactions = transactions.slice(0, 100);

    container.innerHTML = recentTransactions.map(tx => `
        <div class="transaction-item ${tx.tipo}">
            <div class="transaction-header">
                <span class="transaction-type">${tx.tipo}</span>
                <span class="transaction-amount">$${formatNumber(tx.monto)}</span>
            </div>
            <div class="transaction-details">Origen: ${tx.curp_origen || 'N/A'}</div>
            <div class="transaction-details">Destino: ${tx.curp_destino || 'N/A'}</div>
            <div class="transaction-details">
                <span class="transaction-status ${tx.estado}">${tx.estado}</span>
            </div>
            <div class="transaction-date">${formatDate(tx.creado_en)}</div>
        </div>
    `).join('');
}

// Charts Functions
async function loadCharts() {
    await loadTransactionsChart();
    await loadAmountChart();
    await loadTypeChart();
}

async function loadTransactionsChart() {
    try {
        const response = await fetch(`${REPORT_API}/reports/charts/transactions?period=${currentTimeFilter}`, {
            headers: {
                'Authorization': `Bearer ${adminToken}`
            }
        });

        if (response.ok) {
            const data = await response.json();
            renderTransactionsChart(data);
        }
    } catch (error) {
        console.error('Error loading transactions chart:', error);
    }
}

function renderTransactionsChart(data) {
    const ctx = document.getElementById('transactionsChart').getContext('2d');

    if (transactionsChart) {
        transactionsChart.destroy();
    }

    transactionsChart = new Chart(ctx, {
        type: 'line',
        data: {
            labels: data.labels || [],
            datasets: [{
                label: 'Número de Transacciones',
                data: data.values || [],
                borderColor: '#1e3c72',
                backgroundColor: 'rgba(30, 60, 114, 0.1)',
                tension: 0.4,
                fill: true
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
                legend: {
                    display: true
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: {
                        precision: 0
                    }
                }
            }
        }
    });
}

async function loadAmountChart() {
    try {
        const response = await fetch(`${REPORT_API}/reports/charts/amounts?period=${currentTimeFilter}`, {
            headers: {
                'Authorization': `Bearer ${adminToken}`
            }
        });

        if (response.ok) {
            const data = await response.json();
            renderAmountChart(data);
        }
    } catch (error) {
        console.error('Error loading amount chart:', error);
    }
}

function renderAmountChart(data) {
    const ctx = document.getElementById('amountChart').getContext('2d');

    if (amountChart) {
        amountChart.destroy();
    }

    amountChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: data.labels || [],
            datasets: [{
                label: 'Monto Total ($)',
                data: data.values || [],
                backgroundColor: 'rgba(30, 60, 114, 0.7)',
                borderColor: '#1e3c72',
                borderWidth: 2
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
                legend: {
                    display: true
                }
            },
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: {
                        callback: function(value) {
                            return '$' + formatNumber(value);
                        }
                    }
                }
            }
        }
    });
}

async function loadTypeChart() {
    try {
        const response = await fetch(`${REPORT_API}/reports/charts/types`, {
            headers: {
                'Authorization': `Bearer ${adminToken}`
            }
        });

        if (response.ok) {
            const data = await response.json();
            renderTypeChart(data);
        }
    } catch (error) {
        console.error('Error loading type chart:', error);
    }
}

function renderTypeChart(data) {
    const ctx = document.getElementById('typeChart').getContext('2d');

    if (typeChart) {
        typeChart.destroy();
    }

    typeChart = new Chart(ctx, {
        type: 'doughnut',
        data: {
            labels: ['Depósitos', 'Retiros', 'Transferencias'],
            datasets: [{
                data: [
                    data.depositos || 0,
                    data.retiros || 0,
                    data.transferencias || 0
                ],
                backgroundColor: [
                    'rgba(72, 187, 120, 0.8)',
                    'rgba(237, 137, 54, 0.8)',
                    'rgba(66, 153, 225, 0.8)'
                ],
                borderColor: [
                    '#48bb78',
                    '#ed8936',
                    '#4299e1'
                ],
                borderWidth: 2
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            plugins: {
                legend: {
                    position: 'bottom'
                }
            }
        }
    });
}

function changeTimeFilter(filter) {
    currentTimeFilter = filter;

    // Update button styles
    document.querySelectorAll('.filter-btn').forEach(btn => {
        btn.classList.remove('active');
    });
    event.target.classList.add('active');

    // Reload charts
    loadTransactionsChart();
    loadAmountChart();
}

// Utility Functions
function showLoading(show) {
    document.getElementById('loading').style.display = show ? 'flex' : 'none';
}

function showNotification(message, type = 'info') {
    const notification = document.getElementById('notification');
    notification.textContent = message;
    notification.className = `notification ${type} show`;

    setTimeout(() => {
        notification.classList.remove('show');
    }, 4000);
}

function formatNumber(num) {
    return parseFloat(num).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

function formatDate(dateString) {
    const date = new Date(dateString);
    return date.toLocaleString('es-MX', {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    });
}
