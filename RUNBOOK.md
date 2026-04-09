# Devin FinOps Dashboard - Runbook

> Guia operativa completa para desplegar, operar y mantener el Devin FinOps Dashboard.

---

## Tabla de Contenidos

1. [Descripcion del Proyecto](#1-descripcion-del-proyecto)
2. [Prerequisitos](#2-prerequisitos)
3. [Despliegue Local con Docker Compose](#3-despliegue-local-con-docker-compose)
4. [Acceso al Dashboard](#4-acceso-al-dashboard)
5. [Comandos Operativos](#5-comandos-operativos)
6. [Configuracion Avanzada](#6-configuracion-avanzada)
7. [Operaciones Comunes](#7-operaciones-comunes)
8. [Quality Checks](#8-quality-checks)
9. [CI/CD Pipeline](#9-cicd-pipeline)
10. [Deploy a AWS ECS Fargate](#10-deploy-a-aws-ecs-fargate)
11. [Troubleshooting](#11-troubleshooting)
12. [Referencia Rapida](#12-referencia-rapida)

---

## 1. Descripcion del Proyecto

### Stack Tecnologico

| Capa | Tecnologia |
|------|-----------|
| Backend (microservicios) | Java 17, Spring Boot 3, Spring WebFlux, Project Reactor |
| API Gateway | Spring Cloud Gateway |
| Cache / Pub-Sub | Redis 7 |
| Frontend | Angular 17, RxJS, Chart.js |
| Contenedores | Docker, Docker Compose |
| Cloud | AWS ECS Fargate, CloudFormation |
| CI/CD | GitHub Actions |

### Arquitectura

El proyecto sigue una arquitectura de **microservicios reactivos** con el patron **Collector-Distributor**:

```
Devin API v3
     |
     v
data-collector  --(Redis Pub/Sub)-->  websocket-service  --(WebSocket)-->  Frontend (Angular)
     |                                      |
     v                                      v
   Redis <--- sessions-service, billing-service, metrics-service, admin-service
                                            |
                                            v
                                      api-gateway  <-->  Frontend
```

- **data-collector**: Realiza polling contra la API de Devin y publica datos en Redis.
- **websocket-service**: Se suscribe a Redis y retransmite datos en tiempo real via WebSocket a los clientes.
- **sessions-service, billing-service, metrics-service, admin-service**: Servicios REST que exponen datos de cada dominio FinOps.
- **api-gateway (Spring Cloud Gateway)**: Punto de entrada unico que enruta trafico REST y WebSocket a los microservicios.
- **frontend (Angular)**: SPA que consume datos via WebSocket y REST a traves del API Gateway.

### Servicios y Puertos

| Servicio | Puerto interno | Expuesto al host |
|----------|---------------|-------------------|
| Redis | 6379 | Si (`6379:6379`) |
| data-collector | 8081 | No |
| websocket-service | 8082 | No |
| sessions-service | 8083 | No |
| billing-service | 8084 | No |
| metrics-service | 8085 | No |
| admin-service | 8086 | No |
| api-gateway | 8080 | Si (`8080:8080`) |
| frontend | 4200 (host) -> 80 (contenedor) | Si (`4200:80`) |

> **Nota:** Solo los puertos **8080** (API Gateway) y **4200** (Frontend) estan expuestos al host. El resto de servicios solo son accesibles dentro de la red Docker.

### Estructura del Repositorio

```
Devin-FinOps/
├── .github/workflows/ci.yml    # Pipeline CI/CD
├── .env.example                 # Variables de entorno (plantilla)
├── docker-compose.yml           # Orquestacion local
├── endpoints.yaml               # Mapeo de rutas de la API de Devin
├── pom.xml                      # POM padre (reactor Maven)
├── services/
│   ├── api-gateway/             # Spring Cloud Gateway
│   ├── data-collector/          # Polling de la API de Devin
│   ├── websocket-service/       # Broadcast WebSocket via Redis
│   ├── sessions-service/        # REST API - dominio sesiones
│   ├── billing-service/         # REST API - dominio facturacion
│   ├── metrics-service/         # REST API - dominio metricas
│   └── admin-service/           # REST API - dominio administracion
├── shared/                      # Modelos compartidos (DTOs)
├── frontend/                    # Angular 17 SPA
├── scripts/
│   └── check-quality.sh         # Script de quality checks
└── infra/
    ├── deploy-aws.sh            # Script de deploy a AWS ECS Fargate
    ├── cloudformation.yaml      # Plantilla CloudFormation
    ├── helm/                    # Charts de Helm (opcional)
    └── terraform/               # Configuracion Terraform (opcional)
```

---

## 2. Prerequisitos

### Software Requerido

| Herramienta | Version Minima | Verificacion |
|-------------|---------------|-------------|
| Docker | 20.10+ | `docker --version` |
| Docker Compose | 2.0+ (plugin) | `docker compose version` |
| Java JDK | 17 | `java -version` |
| Maven | 3.8+ | `mvn --version` |
| Node.js | 18 | `node --version` |
| npm | 9+ | `npm --version` |
| Angular CLI | 17 | `npx ng version` |
| Git | 2.30+ | `git --version` |
| AWS CLI (solo deploy) | 2.x | `aws --version` |

### Tokens de Devin Service Users

El dashboard necesita **tokens de service users** (NO tokens personales) para autenticarse contra la API de Devin v3:

| Variable | Scope | Donde se provisiona | Permisos requeridos |
|----------|-------|---------------------|---------------------|
| `DEVIN_ENTERPRISE_SERVICE_TOKEN` | Enterprise | [app.devin.ai](https://app.devin.ai) > Enterprise Settings > Service Users | `ViewAccountMetrics`, `ManageBilling`, `ManageEnterpriseSettings` |
| `DEVIN_ORG_SERVICE_TOKEN` | Organization | [app.devin.ai](https://app.devin.ai) > Organization Settings > Service Users | Permisos de org (opcional, si no se configura se omiten endpoints org-scoped) |

> **Importante:** Utilizar siempre `DEVIN_ENTERPRISE_SERVICE_TOKEN` y `DEVIN_ORG_SERVICE_TOKEN`. **NO** usar variables con sufijo `_USER_TOKEN`.

---

## 3. Despliegue Local con Docker Compose

### 3.1 Clonar el repositorio

```bash
git clone https://github.com/Devin-DTT/Devin-FinOps.git
cd Devin-FinOps
```

### 3.2 Configurar variables de entorno

Los tokens se resuelven en este orden de prioridad (mayor a menor):

1. **Variables de entorno del host** (inyectadas automaticamente por Devin Secrets)
2. **`.env`** (archivo local, si existe)
3. **`.env.example`** (valores por defecto / placeholders)

#### Opcion A: Usar Devin Secrets (recomendado)

Si ejecutas el dashboard desde Devin, configura los tokens como **Secrets** en la seccion de Secrets de tu organizacion/sesion con estos nombres:

| Secret | Obligatorio | Descripcion |
|--------|-------------|-------------|
| `DEVIN_ENTERPRISE_SERVICE_TOKEN` | Si | Token de service user enterprise |
| `DEVIN_ORG_SERVICE_TOKEN` | No | Token de service user de organizacion |

Docker Compose pasara automaticamente estas variables de entorno del host a los contenedores. No necesitas crear ni editar ningun archivo `.env`.

#### Opcion B: Usar archivo `.env` (desarrollo local fuera de Devin)

El archivo `.env.example` se encuentra en la **raiz del proyecto** (no en `backend/`).

```bash
# Copiar la plantilla
cp .env.example .env

# Editar con tus tokens reales
nano .env
```

Contenido minimo del `.env`:

```env
# Enterprise service user token (obligatorio)
DEVIN_ENTERPRISE_SERVICE_TOKEN=dv_su_ent_xxxxxxxxxxxxxxxx

# Organization service user token (opcional)
DEVIN_ORG_SERVICE_TOKEN=dv_su_org_xxxxxxxxxxxxxxxx

# (Opcional) Fijar un org_id en lugar de auto-discovery
DEVIN_ORG_ID=

# Intervalos de polling
DASHBOARD_POLLING_INTERVAL_SECONDS=5
DASHBOARD_ORG_DISCOVERY_REFRESH_SECONDS=60
```

> **Nota:** Docker Compose carga `.env.example` (defaults), luego `.env` (si existe), y finalmente las variables de entorno del host tienen la maxima prioridad. Si los tokens estan configurados como Devin Secrets, se inyectan automaticamente sin necesidad de `.env`.

### 3.3 Levantar los servicios

```bash
# Build y arranque de todos los servicios
docker compose up --build

# O en modo detached (background)
docker compose up --build -d
```

### 3.4 Verificacion del despliegue

Verificar que todos los contenedores estan corriendo y healthy:

```bash
# Ver estado de los contenedores
docker compose ps

# Comprobar health de cada servicio
docker inspect --format='{{.Name}}: {{.State.Health.Status}}' \
  $(docker compose ps -q)
```

Resultado esperado: todos los servicios deben mostrar `healthy`.

| Servicio | Health endpoint |
|----------|----------------|
| redis | `redis-cli ping` |
| data-collector | `http://localhost:8081/actuator/health` |
| websocket-service | `http://localhost:8082/actuator/health` |
| sessions-service | `http://localhost:8083/actuator/health` |
| billing-service | `http://localhost:8084/actuator/health` |
| metrics-service | `http://localhost:8085/actuator/health` |
| admin-service | `http://localhost:8086/actuator/health` |
| api-gateway | `http://localhost:8080/actuator/health` |

```bash
# Verificacion rapida del API Gateway desde el host
curl -s http://localhost:8080/actuator/health | jq .
```

---

## 4. Acceso al Dashboard

### URLs

| Recurso | URL |
|---------|-----|
| **Dashboard (Frontend)** | `http://localhost:4200` |
| **API Gateway** | `http://localhost:8080` |
| API Gateway Health | `http://localhost:8080/actuator/health` |

### Comportamiento del Frontend

- Al acceder a `http://localhost:4200`, el frontend **redirige automaticamente a `/dashboard`**.
- La URL final sera: `http://localhost:4200/dashboard`.

### Rutas del API Gateway

El API Gateway (Spring Cloud Gateway) enruta el trafico de la siguiente forma:

| Ruta | Servicio destino | Tipo |
|------|-----------------|------|
| `/api/sessions/**` | sessions-service:8083 | REST |
| `/api/billing/**` | billing-service:8084 | REST |
| `/api/metrics/**` | metrics-service:8085 | REST |
| `/api/admin/**` | admin-service:8086 | REST |
| `/ws/**` | websocket-service:8082 | WebSocket |

### Conexion WebSocket

El frontend establece una conexion WebSocket a traves del API Gateway:

```
ws://localhost:8080/ws
```

El **WebSocket Dispatcher** del frontend recibe frames y los distribuye a los stores reactivos (RxJS) correspondientes para actualizar la UI en tiempo real.

---

## 5. Comandos Operativos

### Gestion de Servicios

```bash
# Arrancar todos los servicios
docker compose up -d

# Parar todos los servicios
docker compose down

# Parar y eliminar volumenes (incluye datos de Redis)
docker compose down -v

# Reiniciar un servicio especifico
docker compose restart data-collector

# Escalar un servicio (ej. 2 instancias del websocket-service)
docker compose up -d --scale websocket-service=2

# Rebuild de un servicio sin cache
docker compose build --no-cache data-collector
docker compose up -d data-collector
```

### Logs

```bash
# Ver logs de todos los servicios
docker compose logs -f

# Ver logs de un servicio especifico
docker compose logs -f data-collector

# Ver ultimas 100 lineas de logs
docker compose logs --tail=100 api-gateway

# Ver logs con timestamps
docker compose logs -f -t websocket-service
```

### Inspeccion de Redis

```bash
# Conectar al CLI de Redis
docker compose exec redis redis-cli

# Dentro de redis-cli:
# Ver todas las claves
KEYS *

# Ver contenido de una clave
GET finops:sessions:latest

# Monitorizar comandos en tiempo real
MONITOR

# Ver informacion del servidor
INFO

# Ver canales Pub/Sub activos
PUBSUB CHANNELS *

# Salir
QUIT
```

### Volcado de datos en bruto (Raw Data Dump)

Para obtener un JSON con todos los datos en bruto que el data-collector ha extraido de la API de Devin:

```bash
# Opcion 1: Usando el script (recomendado)
./scripts/dump-raw-data.sh

# Opcion 2: Usando el endpoint REST via docker
docker compose exec data-collector wget -qO- http://localhost:8081/dump > raw-endpoint-data.json

# Opcion 3: Usando el endpoint REST via API Gateway (expuesto al host)
curl -s http://localhost:8080/api/dump | jq '.' > raw-endpoint-data.json

# Opcion 4: Filtrar solo ciertos endpoints
docker compose exec data-collector wget -qO- "http://localhost:8081/dump?filter=list_sessions*" > sessions-raw.json

# Opcion 5: Usando el script con filtro y ruta personalizada
./scripts/dump-raw-data.sh --filter "list_sessions*" --output ./sessions-raw.json

# El archivo se genera en la raiz del repositorio
cat raw-endpoint-data.json | jq '.total_endpoints'
cat raw-endpoint-data.json | jq '.endpoints | keys'
```

### Shell dentro de un contenedor

```bash
# Entrar en el contenedor del data-collector
docker compose exec data-collector sh

# Entrar en el contenedor del frontend
docker compose exec frontend sh

# Entrar en el contenedor del api-gateway
docker compose exec api-gateway sh
```

---

## 6. Configuracion Avanzada

### Variables del Data Collector

El data-collector soporta configuracion de intervalos de polling a traves de variables de entorno:

| Variable | Descripcion | Valor por defecto |
|----------|-------------|-------------------|
| `COLLECTOR_SESSIONS_POLLING_SECONDS` | Intervalo de polling para sesiones | `5` |
| `COLLECTOR_METRICS_POLLING_SECONDS` | Intervalo de polling para metricas | `30` |
| `COLLECTOR_BILLING_POLLING_SECONDS` | Intervalo de polling para facturacion | `60` |
| `COLLECTOR_ADMIN_POLLING_SECONDS` | Intervalo de polling para administracion | `300` |
| `SPRING_DATA_REDIS_HOST` | Host de Redis | `redis` (nombre del servicio en Docker) |

Para modificar estos valores, anadirlos al `.env`:

```env
COLLECTOR_SESSIONS_POLLING_SECONDS=10
COLLECTOR_METRICS_POLLING_SECONDS=60
COLLECTOR_BILLING_POLLING_SECONDS=120
COLLECTOR_ADMIN_POLLING_SECONDS=600
```

### API Gateway - JWT y CORS

| Variable | Descripcion | Valor por defecto |
|----------|-------------|-------------------|
| `GATEWAY_JWT_ENABLED` | Habilitar validacion JWT en el gateway | `false` |
| `GATEWAY_CORS_ALLOWED_ORIGINS` | Origenes permitidos para CORS | `http://localhost:4200` |

```env
# Habilitar JWT (produccion)
GATEWAY_JWT_ENABLED=true

# Permitir multiples origenes
GATEWAY_CORS_ALLOWED_ORIGINS=http://localhost:4200,https://finops.example.com
```

> **Nota:** En desarrollo local, JWT esta deshabilitado por defecto. Habilitarlo solo para entornos de produccion o staging.

---

## 7. Operaciones Comunes

### Re-discovery de organizaciones

El data-collector descubre automaticamente las organizaciones cada `DASHBOARD_ORG_DISCOVERY_REFRESH_SECONDS` segundos (default: 60).

Para forzar un re-discovery inmediato:

```bash
# Reiniciar el data-collector (fuerza re-discovery al arrancar)
docker compose restart data-collector

# Verificar en los logs que se descubren las organizaciones
docker compose logs -f data-collector | head -50
```

Si se desea fijar una organizacion especifica en lugar de auto-discovery:

```env
# En .env
DEVIN_ORG_ID=org-xxxxxxxxxxxxxxxx
```

### Limpiar cache de Redis

```bash
# Opcion 1: Flush completo (elimina todos los datos cacheados)
docker compose exec redis redis-cli FLUSHALL

# Opcion 2: Eliminar claves de un dominio especifico
docker compose exec redis redis-cli --scan --pattern 'finops:sessions:*' | \
  xargs -L 1 docker compose exec -T redis redis-cli DEL

# Opcion 3: Eliminar volumen de Redis y reiniciar
docker compose down
docker volume rm devin-finops_redis-data
docker compose up -d
```

### Actualizar tokens de service user

Cuando los tokens expiran o se rotan:

```bash
# 1. Editar el archivo .env con los nuevos tokens
nano .env

# 2. Reiniciar los servicios que consumen tokens
docker compose restart data-collector sessions-service billing-service admin-service

# 3. Verificar que los servicios arrancan correctamente
docker compose logs -f data-collector --since 30s
```

> **Importante:** Solo es necesario reiniciar los servicios que usan `env_file`. El api-gateway, websocket-service y frontend no necesitan reinicio.

---

## 8. Quality Checks

### Script de Quality Checks

El script se encuentra en `scripts/check-quality.sh`:

```bash
# Ejecutar todos los quality checks
bash scripts/check-quality.sh
```

Este script ejecuta:
- **Compilacion Maven** de todos los modulos (microservicios + shared)
- **Tests unitarios** con Maven Surefire
- **Checkstyle** y **SpotBugs** para analisis estatico
- **Lint del frontend** con ESLint
- **Build de produccion** del frontend

### Tests

```bash
# Tests de los microservicios (Maven reactor)
mvn test -B

# Tests con informes detallados
mvn test -B -Dsurefire.reportFormat=plain

# Tests de un modulo especifico
mvn test -B -pl services/sessions-service

# Lint del frontend
cd frontend && npm run lint

# Tests del frontend (si estan configurados)
cd frontend && npm test
```

### Build sin Docker

```bash
# Build completo de los microservicios
mvn clean compile -B

# Build del frontend
cd frontend
npm ci --legacy-peer-deps
npx ng build --configuration production
```

---

## 9. CI/CD Pipeline

El pipeline de CI/CD esta definido en `.github/workflows/ci.yml` y se ejecuta automaticamente en cada **push** o **pull request** a la rama `main`.

### Jobs del Pipeline

| Job | Nombre | Descripcion | Runner |
|-----|--------|-------------|--------|
| `microservices` | Microservices - Build & Test | Compila y testea todos los modulos Maven con JDK 17 | `ubuntu-latest` |
| `frontend` | Frontend - Lint & Build | Instala dependencias, lint con ESLint, build de produccion con Angular CLI | `ubuntu-latest` |
| `docker` | Docker - Build Images | Construye las imagenes Docker de cada microservicio y el frontend | `ubuntu-latest` |

### Detalle de cada Job

#### Job: `microservices`

1. Checkout del codigo (`actions/checkout@v4`)
2. Setup JDK 17 Temurin con cache Maven (`actions/setup-java@v4`)
3. `mvn clean compile -B` - Compilar todos los modulos
4. `mvn test -B` - Ejecutar tests unitarios
5. `mvn checkstyle:check spotbugs:check -B` - Analisis de calidad de codigo
6. Upload de resultados de tests como artefactos

#### Job: `frontend`

1. Checkout del codigo (`actions/checkout@v4`)
2. Setup Node.js 18 con cache npm (`actions/setup-node@v4`)
3. `npm ci --legacy-peer-deps` - Instalar dependencias
4. `npm run lint` - Ejecutar ESLint
5. `npx ng build --configuration production` - Build de produccion
6. Upload del directorio `dist/` como artefacto

#### Job: `docker`

> Depende de: `microservices` y `frontend` (solo se ejecuta si ambos pasan).

Construye las imagenes Docker de todos los servicios:
- `devin-finops-api-gateway:ci`
- `devin-finops-data-collector:ci`
- `devin-finops-websocket-service:ci`
- `devin-finops-sessions-service:ci`
- `devin-finops-billing-service:ci`
- `devin-finops-metrics-service:ci`
- `devin-finops-admin-service:ci`
- `devin-finops-frontend:ci`

---

## 10. Deploy a AWS ECS Fargate

### Prerequisitos AWS

- AWS CLI configurado con credenciales apropiadas
- Permisos para crear/gestionar ECS, ECR, CloudFormation, VPC, ALB, Secrets Manager
- Tokens de service user almacenados en **AWS Secrets Manager**

### Configurar Secrets en AWS

```bash
# Almacenar el enterprise token en Secrets Manager
aws secretsmanager create-secret \
  --name devin-finops/enterprise-token \
  --secret-string "dv_su_ent_xxxxxxxxxxxxxxxx"

# Almacenar el org token en Secrets Manager
aws secretsmanager create-secret \
  --name devin-finops/org-token \
  --secret-string "dv_su_org_xxxxxxxxxxxxxxxx"
```

### Deploy con el script

El script de deploy se encuentra en `infra/deploy-aws.sh`:

```bash
# Ver opciones disponibles
bash infra/deploy-aws.sh --help

# Deploy completo
bash infra/deploy-aws.sh

# Deploy con un entorno especifico
bash infra/deploy-aws.sh --env production

# Deploy con una region especifica
bash infra/deploy-aws.sh --region eu-west-1
```

### Infraestructura CloudFormation

La plantilla `infra/cloudformation.yaml` despliega:
- VPC con subnets publicas y privadas
- ECS Cluster con servicios Fargate
- Application Load Balancer (ALB)
- ECR repositories para las imagenes
- ElastiCache (Redis) para cache y Pub/Sub
- Security Groups y IAM Roles
- CloudWatch Log Groups

```bash
# Deploy de la infraestructura con CloudFormation
aws cloudformation deploy \
  --template-file infra/cloudformation.yaml \
  --stack-name devin-finops-stack \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
    Environment=production \
    EnterpriseTokenArn=arn:aws:secretsmanager:REGION:ACCOUNT:secret:devin-finops/enterprise-token
```

---

## 11. Troubleshooting

### Problemas Comunes

#### El frontend no carga o muestra pantalla en blanco

```bash
# Verificar que el frontend esta corriendo
docker compose ps frontend

# Verificar que el API Gateway esta healthy
curl -s http://localhost:8080/actuator/health

# Revisar logs del frontend
docker compose logs frontend

# Verificar que el build de Angular fue exitoso
docker compose logs frontend | head -20
```

#### WebSocket no conecta

```bash
# Verificar que el websocket-service esta healthy
docker compose logs websocket-service

# Verificar que el API Gateway enruta correctamente /ws
curl -i -N \
  -H "Connection: Upgrade" \
  -H "Upgrade: websocket" \
  -H "Sec-WebSocket-Version: 13" \
  -H "Sec-WebSocket-Key: $(openssl rand -base64 16)" \
  http://localhost:8080/ws

# Verificar Redis Pub/Sub
docker compose exec redis redis-cli PUBSUB CHANNELS '*'
```

#### Data collector no recibe datos

```bash
# Verificar tokens
docker compose logs data-collector | rg -i "auth\|token\|unauthorized\|403\|401"

# Verificar conectividad con Redis
docker compose exec data-collector sh -c "wget -qO- http://redis:6379 || echo 'Redis no accesible'"

# Verificar endpoints.yaml montado correctamente
docker compose exec data-collector cat /app/endpoints.yaml
```

#### Servicio reporta `unhealthy`

```bash
# Ver detalles del healthcheck
docker inspect --format='{{json .State.Health}}' devin-finops-data-collector | jq .

# Ver los ultimos logs del health check
docker inspect --format='{{range .State.Health.Log}}{{.Output}}{{end}}' devin-finops-data-collector
```

### Logs de Error

```bash
# Buscar errores en todos los servicios
docker compose logs | rg -i "error\|exception\|fatal"

# Buscar errores en un servicio especifico con contexto
docker compose logs data-collector 2>&1 | rg -i "error" -C 3

# Ver logs desde un momento especifico
docker compose logs --since "2024-01-01T10:00:00" data-collector
```

### Reset Completo

Si el entorno esta en un estado inconsistente:

```bash
# 1. Parar todos los servicios y eliminar contenedores, redes y volumenes
docker compose down -v

# 2. Eliminar imagenes construidas (opcional)
docker compose down --rmi local

# 3. Limpiar cache de Docker (opcional, libera espacio)
docker system prune -f

# 4. Reconstruir desde cero
docker compose up --build -d

# 5. Verificar que todo esta healthy
docker compose ps
```

---

## 12. Referencia Rapida

### Comandos Mas Usados

```bash
# --- Ciclo de vida ---
docker compose up --build -d          # Build y arrancar todo
docker compose down                    # Parar todo
docker compose down -v                 # Parar y borrar datos
docker compose restart <servicio>      # Reiniciar un servicio

# --- Logs ---
docker compose logs -f                 # Todos los logs
docker compose logs -f data-collector  # Logs de un servicio

# --- Estado ---
docker compose ps                      # Estado de contenedores

# --- Redis ---
docker compose exec redis redis-cli    # CLI de Redis

# --- Quality ---
bash scripts/check-quality.sh          # Quality checks completos
mvn test -B                            # Tests de microservicios
cd frontend && npm run lint            # Lint del frontend

# --- Deploy ---
bash infra/deploy-aws.sh               # Deploy a AWS ECS Fargate
```

### URLs Importantes

| Recurso | URL |
|---------|-----|
| Dashboard | `http://localhost:4200` |
| Dashboard (redireccion) | `http://localhost:4200/dashboard` |
| API Gateway | `http://localhost:8080` |
| API Gateway Health | `http://localhost:8080/actuator/health` |
| WebSocket | `ws://localhost:8080/ws` |
| Devin API Docs | [https://docs.devin.ai](https://docs.devin.ai) |
| Service Users (Enterprise) | [https://app.devin.ai](https://app.devin.ai) > Enterprise Settings > Service Users |
| Service Users (Org) | [https://app.devin.ai](https://app.devin.ai) > Organization Settings > Service Users |
| CI/CD Pipeline | `.github/workflows/ci.yml` |
| Quality Checks | `scripts/check-quality.sh` |
| Deploy Script | `infra/deploy-aws.sh` |

### Variables de Entorno Clave

```env
# Tokens (obligatorios para datos reales)
DEVIN_ENTERPRISE_SERVICE_TOKEN=<token-enterprise>
DEVIN_ORG_SERVICE_TOKEN=<token-org>

# Polling intervals
COLLECTOR_SESSIONS_POLLING_SECONDS=5
COLLECTOR_METRICS_POLLING_SECONDS=30
COLLECTOR_BILLING_POLLING_SECONDS=60
COLLECTOR_ADMIN_POLLING_SECONDS=300

# API Gateway
GATEWAY_JWT_ENABLED=false
GATEWAY_CORS_ALLOWED_ORIGINS=http://localhost:4200
```

---

> **Ultima actualizacion:** Abril 2026 | **Repositorio:** [Devin-DTT/Devin-FinOps](https://github.com/Devin-DTT/Devin-FinOps)
