-- Esquema de base de datos para MisCuentas

CREATE TABLE IF NOT EXISTS usuarios (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    firebase_uid TEXT UNIQUE NOT NULL,
    nombre TEXT NOT NULL,
    email TEXT NOT NULL,
    moneda TEXT DEFAULT 'EUR',
    tema TEXT DEFAULT 'claro'
);

CREATE TABLE IF NOT EXISTS cuentas (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id INTEGER NOT NULL,
    nombre TEXT NOT NULL,
    tipo TEXT NOT NULL CHECK (tipo IN ('banco', 'efectivo')),
    saldo_inicial REAL DEFAULT 0,
    moneda TEXT DEFAULT 'EUR',
    activa INTEGER DEFAULT 1,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS categorias (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id INTEGER NOT NULL,
    nombre TEXT NOT NULL,
    tipo TEXT NOT NULL CHECK (tipo IN ('ingreso', 'gasto')),
    icono TEXT,
    predefinida INTEGER DEFAULT 0,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS movimientos (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id INTEGER NOT NULL,
    cuenta_id INTEGER NOT NULL,
    categoria_id INTEGER NOT NULL,
    importe REAL NOT NULL,
    tipo TEXT NOT NULL CHECK (tipo IN ('ingreso', 'gasto')),
    fecha TEXT NOT NULL,
    descripcion TEXT,
    sincronizado INTEGER DEFAULT 0,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    FOREIGN KEY (cuenta_id) REFERENCES cuentas(id),
    FOREIGN KEY (categoria_id) REFERENCES categorias(id)
);

CREATE TABLE IF NOT EXISTS presupuestos (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id INTEGER NOT NULL,
    categoria_id INTEGER NOT NULL,
    limite REAL NOT NULL,
    periodo TEXT DEFAULT 'mensual',
    mes_referencia TEXT NOT NULL,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    FOREIGN KEY (categoria_id) REFERENCES categorias(id)
);

CREATE TABLE IF NOT EXISTS recurrentes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id INTEGER NOT NULL,
    cuenta_id INTEGER NOT NULL,
    categoria_id INTEGER NOT NULL,
    importe REAL NOT NULL,
    tipo TEXT NOT NULL CHECK (tipo IN ('ingreso', 'gasto')),
    frecuencia TEXT NOT NULL,
    dia_ejecucion INTEGER,
    activo INTEGER DEFAULT 1,
    ultima_ejecucion TEXT,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    FOREIGN KEY (cuenta_id) REFERENCES cuentas(id),
    FOREIGN KEY (categoria_id) REFERENCES categorias(id)
);

CREATE INDEX IF NOT EXISTS idx_movimientos_cuenta_fecha ON movimientos(cuenta_id, fecha);
CREATE INDEX IF NOT EXISTS idx_presupuestos_usuario_mes ON presupuestos(usuario_id, mes_referencia);
