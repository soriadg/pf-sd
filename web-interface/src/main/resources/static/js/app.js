// Configuration
const API_BASE_URL = 'http://localhost:8080'; // AccountService
const AUTH_BASE_URL = 'http://localhost:8081'; // AuthService

// State
let token = localStorage.getItem('token');
let currentUser = localStorage.getItem('curp');

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

// Initialize app
document.addEventListener('DOMContentLoaded', () => {
    if (token && currentUser) {
        showDashboard();
        loadBalance();
        loadTransactions();
    }
});

// Auth Functions
function showLogin() {
    document.getElementById('login-form').style.display = 'block';
    document.getElementById('register-form').style.display = 'none';
}

function showRegister() {
    document.getElementById('login-form').style.display = 'none';
    document.getElementById('register-form').style.display = 'block';
}

async function register(event) {
    event.preventDefault();

    const curp = normalizeCurp(document.getElementById('register-curp').value);
    const password = document.getElementById('register-password').value;
    const confirmPassword = document.getElementById('register-password-confirm').value;

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

    if (password !== confirmPassword) {
        showNotification('Las contraseñas no coinciden', 'error');
        return;
    }

    showLoading(true);

    try {
        const response = await fetch(`${AUTH_BASE_URL}/auth/register`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                curp: curp,
                contrasena: password,
                rol: 'USUARIO'
            })
        });

        const data = await parseJsonSafe(response);

        if (response.ok) {
            showNotification('Registro exitoso. Ahora puedes iniciar sesión', 'success');
            const registerForm = document.getElementById('register-form');
            if (registerForm && registerForm.reset) {
                registerForm.reset();
            }
            showLogin();
        } else {
            showNotification(data.error || `Error al registrar usuario (${response.status})`, 'error');
        }
    } catch (error) {
        showNotification('Error de conexión: ' + error.message, 'error');
    } finally {
        showLoading(false);
    }
}

async function login(event) {
    event.preventDefault();

    const curp = normalizeCurp(document.getElementById('login-curp').value);
    const password = document.getElementById('login-password').value;

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
            token = data.token;
            currentUser = curp;
            localStorage.setItem('token', token);
            localStorage.setItem('curp', curp);

            showNotification('Inicio de sesión exitoso', 'success');
            showDashboard();
            loadBalance();
            loadTransactions();

            document.getElementById('login-form').reset();
        } else {
            showNotification(data.error || `Error al iniciar sesión (${response.status})`, 'error');
        }
    } catch (error) {
        showNotification('Error de conexión: ' + error.message, 'error');
    } finally {
        showLoading(false);
    }
}

function logout() {
    token = null;
    currentUser = null;
    localStorage.removeItem('token');
    localStorage.removeItem('curp');

    document.getElementById('auth-section').style.display = 'flex';
    document.getElementById('dashboard-section').style.display = 'none';

    showNotification('Sesión cerrada', 'info');
}

function showDashboard() {
    document.getElementById('auth-section').style.display = 'none';
    document.getElementById('dashboard-section').style.display = 'block';
    document.getElementById('user-curp').textContent = currentUser;
}

// Balance Functions
async function loadBalance() {
    try {
        const response = await fetch(`${API_BASE_URL}/account/balance`, {
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        if (response.ok) {
            const data = await response.json();
            document.getElementById('saldo-banco').textContent = `$${formatNumber(data.saldo_banco)}`;
            document.getElementById('saldo-billetera').textContent = `$${formatNumber(data.saldo_billetera)}`;
            document.getElementById('saldo-total').textContent = `$${formatNumber(data.saldo_banco + data.saldo_billetera)}`;
        } else if (response.status === 401) {
            showNotification('Sesión expirada', 'error');
            logout();
        }
    } catch (error) {
        console.error('Error loading balance:', error);
    }
}

// Transaction Functions
async function deposit(event) {
    event.preventDefault();

    const amount = parseFloat(document.getElementById('deposit-amount').value);

    if (amount <= 0) {
        showNotification('El monto debe ser mayor a 0', 'error');
        return;
    }

    showLoading(true);

    try {
        const response = await fetch(`${API_BASE_URL}/account/deposit`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({
                monto: amount
            })
        });

        const data = await response.json();

        if (response.ok) {
            showNotification('Depósito realizado exitosamente', 'success');
            document.getElementById('deposit-amount').value = '';
            setTimeout(() => {
                loadBalance();
                loadTransactions();
            }, 2000);
        } else {
            showNotification(data.error || 'Error al realizar depósito', 'error');
        }
    } catch (error) {
        showNotification('Error de conexión: ' + error.message, 'error');
    } finally {
        showLoading(false);
    }
}

async function withdraw(event) {
    event.preventDefault();

    const amount = parseFloat(document.getElementById('withdraw-amount').value);

    if (amount <= 0) {
        showNotification('El monto debe ser mayor a 0', 'error');
        return;
    }

    showLoading(true);

    try {
        const response = await fetch(`${API_BASE_URL}/account/withdraw`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({
                monto: amount
            })
        });

        const data = await response.json();

        if (response.ok) {
            showNotification('Retiro realizado exitosamente', 'success');
            document.getElementById('withdraw-amount').value = '';
            setTimeout(() => {
                loadBalance();
                loadTransactions();
            }, 2000);
        } else {
            showNotification(data.error || 'Error al realizar retiro', 'error');
        }
    } catch (error) {
        showNotification('Error de conexión: ' + error.message, 'error');
    } finally {
        showLoading(false);
    }
}

async function transfer(event) {
    event.preventDefault();

    const destCurp = document.getElementById('transfer-curp').value.toUpperCase();
    const amount = parseFloat(document.getElementById('transfer-amount').value);

    if (destCurp === currentUser) {
        showNotification('No puedes transferir a ti mismo', 'error');
        return;
    }

    if (amount <= 0) {
        showNotification('El monto debe ser mayor a 0', 'error');
        return;
    }

    showLoading(true);

    try {
        const response = await fetch(`${API_BASE_URL}/account/transfer`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({
                curpDestino: destCurp,
                monto: amount
            })
        });

        const data = await response.json();

        if (response.ok) {
            showNotification('Transferencia realizada exitosamente', 'success');
            document.getElementById('transfer-curp').value = '';
            document.getElementById('transfer-amount').value = '';
            setTimeout(() => {
                loadBalance();
                loadTransactions();
            }, 5000);
        } else {
            showNotification(data.error || 'Error al realizar transferencia', 'error');
        }
    } catch (error) {
        showNotification('Error de conexión: ' + error.message, 'error');
    } finally {
        showLoading(false);
    }
}

async function loadTransactions() {
    try {
        const response = await fetch(`${API_BASE_URL}/account/transactions`, {
            headers: {
                'Authorization': `Bearer ${token}`
            }
        });

        if (response.ok) {
            const transactions = await response.json();
            displayTransactions(transactions);
        }
    } catch (error) {
        console.error('Error loading transactions:', error);
    }
}

function displayTransactions(transactions) {
    const container = document.getElementById('transactions-list');

    if (!transactions || transactions.length === 0) {
        container.innerHTML = '<p class="no-data">No hay transacciones</p>';
        return;
    }

    container.innerHTML = transactions.map(tx => {
        const isIncoming = tx.curp_destino === currentUser;
        const isOutgoing = tx.curp_origen === currentUser;
        const amountClass = isIncoming ? 'positive' : 'negative';
        const amountSign = isIncoming ? '+' : '-';

        let typeLabel = tx.tipo;
        let details = '';

        if (tx.tipo === 'DEPOSITO') {
            typeLabel = 'Depósito';
            details = 'De banco a billetera';
        } else if (tx.tipo === 'RETIRO') {
            typeLabel = 'Retiro';
            details = 'De billetera a banco';
        } else if (tx.tipo === 'TRANSFERENCIA') {
            if (isIncoming) {
                typeLabel = 'Transferencia Recibida';
                details = `De: ${tx.curp_origen}`;
            } else {
                typeLabel = 'Transferencia Enviada';
                details = `Para: ${tx.curp_destino}`;
            }
        }

        return `
            <div class="transaction-item ${tx.tipo}">
                <div class="transaction-header">
                    <span class="transaction-type">${typeLabel}</span>
                    <span class="transaction-amount ${amountClass}">${amountSign}$${formatNumber(tx.monto)}</span>
                </div>
                <div class="transaction-details">${details}</div>
                <div class="transaction-details">Estado: ${tx.estado}</div>
                <div class="transaction-date">${formatDate(tx.creado_en)}</div>
            </div>
        `;
    }).join('');
}

// Tab Functions
function showTab(tabName) {
    const tabs = document.querySelectorAll('.tab-btn');
    const contents = document.querySelectorAll('.tab-content');

    tabs.forEach(tab => tab.classList.remove('active'));
    contents.forEach(content => content.classList.remove('active'));

    event.target.classList.add('active');
    document.getElementById(`${tabName}-tab`).classList.add('active');
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
        minute: '2-digit'
    });
}
