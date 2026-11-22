# Despliegue · Pokedex+

Frontend en **Vercel**, backend Spring Boot en **Render** (Docker, plan gratuito) y PostgreSQL en **Neon** (plan gratuito). Las tres piezas quedan en **US East**: Render en Virginia, Neon en `aws-us-east-1` y las funciones de Vercel en `iad1`. Por qué esta combinación y no AWS: `docs/decisiones.md` (2026-11-21).

> **Regla de oro:** los valores de las variables (contraseñas, claves, secretos) solo viven en los paneles de Render y Vercel y en tu `application-local.properties`. En los documentos y en Git va únicamente el **nombre**.

## 1. Arquitectura

```mermaid
flowchart LR
    U[Usuario<br/>navegador] -->|HTTPS| V
    subgraph Vercel["Vercel · iad1"]
        V[Next.js 16<br/>páginas + /api/identificar]
    end
    subgraph Render["Render · Virginia"]
        R[Spring Boot 4<br/>contenedor Docker]
    end
    subgraph Neon["Neon · us-east-1"]
        P[(PostgreSQL 18<br/>branch production)]
        D[(PostgreSQL 18<br/>branch dev)]
    end
    V -->|API_URL · JSON| R
    R -->|JDBC + SSL| P
    R -.->|solo dev| D
    R -->|visión · GEMINI_API_KEY| G[Gemini API]
    R -.->|solo al importar| PK[PokeAPI]
    GH[GitHub<br/>main / master] -->|Actions: CI| CI{{CI en verde}}
    CI -->|Auto-Deploy<br/>After CI Checks Pass| R
    GH -->|integración Git| V
```

El navegador **nunca** llama a la API: las páginas y el route handler `/api/identificar` corren en el servidor de Next y reenvían a `API_URL`. Por eso CORS solo importa para clientes de navegador directos. La URL del backend no sale de Vercel.

### Equivalencias en AWS

| Hoy | En AWS | Nota |
|---|---|---|
| Vercel (Next.js + CDN) | CloudFront + AWS Amplify Hosting (o S3 + Lambda@Edge) | Amplify despliega Next.js desde Git |
| Render (contenedor Docker) | ECS Fargate o App Runner, detrás de un ALB | La misma imagen del `Dockerfile` |
| Neon (PostgreSQL) | RDS for PostgreSQL o Aurora Serverless v2 | `unaccent` viene incluida |
| Variables de entorno de Render/Vercel | Secrets Manager / SSM Parameter Store | Se inyectan como variables del contenedor |
| Health Check Path `/api/health` | Health check del target group del ALB | Mismo endpoint |
| GitHub Actions (CI) | CodeBuild + CodePipeline | Los mismos comandos (`mvnw verify`, `docker build`) |
| Render Rollback / Vercel Instant Rollback | ECS: redeploy de la task definition anterior | Rollback de la app, no de la base |

## 2. Neon (base de datos)

Proyecto `pokedex`, AWS `us-east-1`, PostgreSQL 18, con dos branches: **production** (producción) y **dev** (local y Preview).

1. En cada branch, la extensión `unaccent` (la app también la crea al arrancar con `schema.sql`, pero conviene tenerla desde el inicio):
   ```sql
   CREATE EXTENSION IF NOT EXISTS unaccent;
   ```
2. Los datos llegaron con `pg_dump` (formato custom, `-Fc`) desde la base local y `pg_restore` hacia cada branch, no reimportando desde la PokeAPI (decisión del 2026-11-21).
3. **El dump trae el administrador local con su contraseña local.** `AdministradorInicial` solo crea el admin si no existe y nunca pisa uno existente, así que en producción hay que borrarlo **antes del primer arranque** (ver §7, paso 1):
   ```sql
   DELETE FROM usuario WHERE usuario = '<valor de ADMIN_USERNAME>';
   ```
   Al arrancar, el servicio lo recrea con `ADMIN_USERNAME` y `ADMIN_PASSWORD` de Render. **Cambiar la contraseña de producción después:** cambia `ADMIN_PASSWORD` en Render, ejecuta el mismo `DELETE` en la consola SQL del branch production y reinicia el servicio. (La rama `dev` puede conservar el admin local.)
4. **Cadena de conexión.** Neon muestra la URL `postgresql://usuario:clave@host/base`. El backend la pide **separada y en formato JDBC**:
   - `SPRING_DATASOURCE_URL` = `jdbc:postgresql://<host>/<base>?sslmode=require` (sin usuario ni contraseña dentro)
   - `SPRING_DATASOURCE_USERNAME` y `SPRING_DATASOURCE_PASSWORD` aparte.
   - Usa el host **directo** (sin `-pooler`): el pooler de Neon trabaja en modo transacción y complica a Hibernate. El pool de Hikari del backend ya es pequeño (5 conexiones máximo).
5. **Suspensión.** Neon suspende el cómputo a los 5 min sin conexiones y despierta solo (unos segundos). Por eso `DB_POOL_MIN` vale 0: sin conexiones abiertas, Neon puede dormir y no se gastan las horas gratuitas.
6. Antes de desplegar un cambio que toque entidades (`ddl-auto=update` también en producción), crea un respaldo con `pg_dump` o una *branch* nueva de Neon desde `production`.

## 3. Render (backend), dos servicios

Se crean **dos Web Services** con la misma configuración y distintas variables.

| Ajuste | `pokedex-api-prod` | `pokedex-api-dev` |
|---|---|---|
| Repositorio | `LoadingCCat/pokedex-backend` | el mismo |
| Rama | `main` | `dev` (créala desde `main`) o `main` si prefieres un solo flujo |
| Runtime | Docker (usa el `Dockerfile` de la raíz) | igual |
| Región | Virginia (US East) | igual |
| Plan | Free | Free |
| Health Check Path | `/api/health` | `/api/health` |
| **Auto-Deploy** | **After CI Checks Pass** | **After CI Checks Pass** |
| Base de datos | Neon branch `production` | Neon branch `dev` |

- **Auto-Deploy «After CI Checks Pass»**: Render espera a que el CI de GitHub Actions termine en verde antes de desplegar. Un push con el CI en rojo no se despliega. (El CI debe haber corrido al menos una vez.)
- No pongas `PORT`: Render lo define y la app lo lee (`server.port=${PORT:8080}`).
- **No pongas `SPRING_PROFILES_ACTIVE=e2e`** nunca: ese perfil simula la visión y siempre responde Charmander.
- Plan gratuito: el servicio se duerme tras 15 min sin peticiones y tarda hasta un minuto en despertar; el frontend lo tolera (esperas de 70 s y 110 s para identificar). No uses un cron que lo mantenga despierto: Render lo considera abuso del plan gratuito.

## 4. Vercel (frontend)

El proyecto `pokedex-frontend` se despliega solo desde `master`. Sin `API_URL` corre en modo mock (datos de ejemplo; `/identificar` y `/admin` muestran un aviso).

- **Production**: `API_URL` = `https://<servicio-prod>.onrender.com/api`
- **Preview**: `API_URL` = `https://<servicio-dev>.onrender.com/api`
- Tras cambiar una variable hay que **redesplegar**: las variables se leen al compilar y arrancar la función.
- Con `API_URL`, el build **no** llama a la API (los datos van dentro de `<Suspense>`), así que compila aunque Render esté dormido. Solo se prerenderizan las 151 fichas de Kanto; el resto se genera en la primera visita.
- Funciones: `maxDuration = 120` en `/api/identificar` (Hobby con Fluid Compute); el fetch a la API se corta a los 110 s y el usuario recibe un mensaje claro.
- Uso del plan Hobby: vigila **Settings → Usage** (almacenamiento de despliegues e Image Optimization). Para bajar el almacenamiento, borra despliegues antiguos y reduce la retención (**Settings → Security → Deployment Retention**).

## 5. Variables de entorno (solo nombres)

| Variable | Render prod | Render dev | Vercel Production | Vercel Preview | Local | Para qué |
|---|:-:|:-:|:-:|:-:|:-:|---|
| `SPRING_DATASOURCE_URL` | ✔ | ✔ | | | opc. | URL JDBC de Neon con `?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | ✔ | ✔ | | | opc. | Usuario de Neon |
| `SPRING_DATASOURCE_PASSWORD` | ✔ | ✔ | | | opc. | Contraseña de Neon |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | | | | | ✔ | Respaldo para la base local (o `application-local.properties`) |
| `JWT_SECRETO` | ✔ | ✔ | | | ✔ | Firma del token (≥ 32 caracteres, **distinto en cada entorno**) |
| `ADMIN_USERNAME` | ✔ | ✔ | | | ✔ | Admin inicial (se crea si no existe) |
| `ADMIN_PASSWORD` | ✔ | ✔ | | | ✔ | Contraseña del admin inicial |
| `CORS_ORIGENES` | ✔ | ✔ | | | opc. | Dominios del front que llaman desde el navegador, separados por comas |
| `GEMINI_API_KEY` | ✔ | ✔ | | | ✔ | Clave de visión (sin ella, identificar responde 502) |
| `GEMINI_MODELO` | opc. | opc. | | | opc. | Modelo de Gemini (por defecto el de `application.properties`) |
| `DB_POOL_MAX`, `DB_POOL_MIN` | opc. | opc. | | | opc. | Pool de Hikari (por defecto 5 y 0) |
| `server.tomcat.remoteip.internal-proxies` (como `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES`) | solo si falla §6.3 | | | | | Proxies de confianza para la IP real |
| `PORT` | automática | automática | | | | Puerto: lo pone Render |
| `API_URL` | | | ✔ | ✔ | opc. | URL de la API (con `/api`); sin ella, modo mock |
| `DEV_ORIGINS` | | | | | opc. | Solo `next dev`: IP para probar en el celular |
| `E2E_ADMIN_USUARIO`, `E2E_ADMIN_CONTRASENA` | | | | | ✔ | Solo pruebas E2E locales (`.env.test`) |

`CORS_ORIGENES` en prod: el dominio de producción de Vercel (`https://pokedex-frontend-seven.vercel.app`). En dev: déjala vacía o con `http://localhost:3000`. Las páginas de Vercel (incluidos los Preview) llaman a la API desde su servidor, donde CORS no aplica.

## 6. Verificación

1. **Salud.** `GET https://<servicio>.onrender.com/api/health` → `{"estado":"ok"}`. La primera llamada tras una pausa puede tardar ~1 min.
2. **Datos.** `GET /api/pokemons?size=1` → 200 con `totalElements` = 1025, y una búsqueda sin tildes (`/api/pokemons?nombre=pikachu`) para confirmar `unaccent`.
3. **IP real y límite (pendiente desde la decisión del 2026-11-21).** El límite de 10 fotos por minuto por IP usa `request.getRemoteAddr()` con `forward-headers-strategy=native` (Tomcat lee `X-Forwarded-For` de derecha a izquierda y se detiene en el primer proxy no interno). Hay que comprobar que los proxies de Render caen en `internal-proxies`:
   - Manda **11** `POST /api/pokemons/identificar` (con una foto pequeña) cambiando cada vez un `X-Forwarded-For` falso (`1.1.1.1`, `1.1.1.2`, …). **Esperado:** la 11.ª responde **429**. Si nunca llega a 429, el servidor se deja engañar por la cabecera: ajusta `server.tomcat.remoteip.internal-proxies`.
   - Desde otra red (por ejemplo el celular con datos), 3 envíos seguidos deben responder 200: el cupo es por cliente y no compartido. Si todos comparten cupo, `getRemoteAddr()` está viendo la IP del proxy: ajusta lo mismo.
4. **Identificación real.** En `https://<tu-dominio>/identificar` sube la foto de un Pokémon: debe mostrar el identificado y 3 similares (usa `GEMINI_API_KEY` real). Con Render dormido verás «Despertando el servidor…» a los 5 s.
5. **Panel.** Inicia sesión en `/login` con el `ADMIN_USERNAME` / `ADMIN_PASSWORD` **de producción** (no con la contraseña local: confirma que el `DELETE` del paso 3 de §2 funcionó).
6. **E2E.** Las pruebas Playwright no corren en el CI (necesitan el backend con perfil `e2e` y la base con los 1025). Se lanzan a mano: `npm run test:e2e` contra local, o contra el sitio desplegado cambiando `baseURL`/`API_URL` (no uses el perfil `e2e` en Render).

## 7. Orden del primer despliegue

1. En Neon, branch **production**: `DELETE` del admin (§2.3).
2. Crear el servicio Render **dev** y su variables; esperar el deploy; verificar §6.1–6.3.
3. Crear el servicio Render **prod** y sus variables; verificar §6.1–6.3.
4. En Vercel, añadir `API_URL` (Production y Preview) y redesplegar; verificar §6.4–6.5.
5. Protección de ramas (§8) tras el primer CI en verde.

## 8. Proteger `main` y `master` (GitHub)

Repos públicos: aplica. Se usa un *ruleset* (permite que tú, como dueño, sigas haciendo push directo) en cada repo:

1. Repo → **Settings → Rules → Rulesets → New ruleset → New branch ruleset**.
2. Nombre `proteger-main` (en el frontend, `proteger-master`); **Enforcement status: Active**.
3. **Bypass list → Add bypass → Repository admin**.
4. **Target branches → Add target → Include default branch**.
5. Reglas: marca **Restrict deletions** y **Block force pushes**; marca **Require status checks to pass** y agrega `test` y `docker` (backend) o `build` (frontend). Estos nombres solo aparecen después de que el CI haya corrido una vez. **No** marques *Require a pull request*.
6. **Create**.

Con el bypass de administrador, tus pushes directos no esperan al CI: el ruleset protege contra borrado, force-push y PR sin CI en verde. Por eso en Render el Auto-Deploy es *After CI Checks Pass*: es la barrera real para que un push en rojo no llegue a producción.

## 9. Rollback

- **Render:** servicio → **Events/Deploys** → el último despliegue bueno → **Rollback**. Es instantáneo (reutiliza la imagen anterior). Desactiva el Auto-Deploy si no quieres que el siguiente push vuelva a desplegar lo roto.
- **Vercel:** proyecto → **Deployments** → el despliegue bueno → **Instant Rollback** (o **Promote to Production**).
- **Base de datos:** el rollback de la app **no** revierte la base. Antes de un cambio de entidades, respaldo con `pg_dump` o una *branch* de Neon; si hace falta, restaurar desde ahí.
- Simulacros pendientes: B.54 (Render) y FE.54 (Vercel) en `docs/tareas.md`.
