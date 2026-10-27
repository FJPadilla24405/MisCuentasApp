-- Esquema de base de datos para MisCuentas

CREATE TABLE IF NOT EXISTS usuarios (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    firebase_uid VARCHAR(255) UNIQUE NOT NULL,
    nombre VARCHAR(50) NOT NULL,
    email VARCHAR(50) NOT NULL,
    moneda CHAR(3) DEFAULT 'EUR',
    tema VARCHAR(10) DEFAULT 'claro',
    password_hash VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS cuentas (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id INTEGER NOT NULL,
    nombre VARCHAR(50) NOT NULL,
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('banco', 'efectivo')),
    saldo_inicial DECIMAL(12,2) DEFAULT 0,
    moneda CHAR(3) DEFAULT 'EUR',
    activa TINYINT DEFAULT 1,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS categorias (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id INTEGER NOT NULL,
    nombre VARCHAR(50) NOT NULL,
    subcat VARCHAR(50),
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('ingreso', 'gasto')),
    predefinida TINYINT DEFAULT 0,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS movimientos (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id INTEGER NOT NULL,
    cuenta_id INTEGER NOT NULL,
    categoria_id INTEGER NOT NULL,
    importe DECIMAL(12,2) NOT NULL,
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('ingreso', 'gasto')),
    fecha DATE NOT NULL,
    descripcion VARCHAR(100),
    sincronizado TINYINT DEFAULT 0,
    recurrente_id INTEGER,  -- NULL = puntual
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    FOREIGN KEY (cuenta_id) REFERENCES cuentas(id),
    FOREIGN KEY (categoria_id) REFERENCES categorias(id),
    FOREIGN KEY (recurrente_id) REFERENCES recurrentes(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS presupuestos (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    usuario_id INTEGER NOT NULL,
    categoria_id INTEGER NOT NULL,
    limite DECIMAL(12,2) NOT NULL,
    periodo VARCHAR(20) DEFAULT 'mensual',
    mes_referencia VARCHAR(20) NOT NULL,
    FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    FOREIGN KEY (categoria_id) REFERENCES categorias(id)
);

CREATE TABLE IF NOT EXISTS recurrentes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    frecuencia VARCHAR(20) NOT NULL,
    dia_ejecucion INTEGER,
    activo TINYINT DEFAULT 1,
    ultima_ejecucion DATE
);

CREATE INDEX IF NOT EXISTS idx_movimientos_cuenta_fecha ON movimientos(cuenta_id, fecha);
CREATE INDEX IF NOT EXISTS idx_presupuestos_usuario_mes ON presupuestos(usuario_id, mes_referencia);
CREATE INDEX IF NOT EXISTS idx_movimientos_recurrente ON movimientos(recurrente_id);
