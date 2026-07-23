# Task Manager IA

Backend de una aplicacion de gestion de tareas academicas que permite crear y completar tareas hablando en lenguaje natural. Un modelo de IA interpreta el texto (transcrito por voz o escrito), extrae la informacion estructurada, la guarda en base de datos, y sincroniza automaticamente con el Google Calendar del propio usuario. El sistema es completamente multi-usuario: cada persona se registra con su cuenta, conecta su propio Google Calendar, y sus tareas quedan aisladas del resto.

**Backend en produccion**: https://task-manager-ia-backend.onrender.com
**Repositorio mobile**: https://github.com/Yloaiza/task-manager-mobile

## Flujo completo del sistema

1. El usuario abre la app mobile y se registra o inicia sesion (email + contraseña)
2. El backend genera un token JWT que la app guarda de forma segura en el dispositivo (Secure Store)
3. Opcionalmente, el usuario conecta su propio Google Calendar desde la app (flujo OAuth2 completo, cada usuario autoriza su propia cuenta)
4. El usuario toca el microfono y dice algo como *"Tengo examen de calculo el 20, es dificil"*
5. El reconocimiento de voz nativo transcribe el audio a texto, y detecta automaticamente el silencio para enviar el comando sin necesidad de tocar ningun boton adicional
6. La app envia el texto al backend junto con el token de sesion
7. El backend le pasa el texto a Groq (Llama 3.3 70B) junto con la lista de tareas pendientes del usuario, y el modelo decide si el usuario quiere **crear** una tarea nueva o **completar** una existente, comparando semanticamente contra los titulos ya guardados
8. Si es una tarea nueva: se guarda en PostgreSQL asociada al usuario, y se crea un evento en el Google Calendar de ese usuario especifico (usando su propio token OAuth)
9. Si es completar una tarea: se marca como completada
10. La app programa una notificacion local para recordar la tarea 1 hora antes de su vencimiento

## Arquitectura

App Mobile (Expo / React Native)
|
| HTTPS + JWT Bearer token
v
Backend (Spring Boot, Render)
|
|--- PostgreSQL (Supabase) — usuarios, tareas, credenciales de Google por usuario
|--- Groq API — extraccion de datos y decision crear/completar
|--- Google Calendar API (OAuth2 web, por usuario) — sincronizacion de eventos
|
+--- n8n (local) — recordatorios automaticos y resumen semanal por email


## Stack

- **Backend**: Java 17 + Spring Boot 4
- **Seguridad**: Spring Security + JWT (io.jsonwebtoken), BCrypt para contraseñas
- **Base de datos**: PostgreSQL (Supabase, conexion via Session Pooler / IPv4)
- **IA**: Groq API (Llama 3.3 70B) con prompts estructurados para extraccion y clasificacion de intencion
- **Calendario**: Google Calendar API (OAuth2 para aplicaciones web, un token por usuario)
- **Deploy**: Docker sobre Render
- **Automatizacion**: n8n (recordatorios y resumenes por email, documentado como funcional en entorno local)

## Autenticacion de usuarios

- `POST /api/auth/register` — crea una cuenta (nombre, email, contraseña hasheada con BCrypt)
- `POST /api/auth/login` — devuelve un token JWT valido por 7 dias
- Todos los endpoints de `/api/tasks/**` y `/api/calendar/**` (salvo el callback) requieren `Authorization: Bearer <token>`
- Cada tarea pertenece a un usuario (`user_id`); los endpoints filtran automaticamente por el usuario autenticado

## Google Calendar por usuario

- `GET /api/calendar/connect` — redirige al usuario autenticado a la pantalla de consentimiento de Google
- `GET /api/calendar/callback` — recibe el codigo de autorizacion de Google, lo intercambia por tokens (access + refresh), y los guarda asociados a ese usuario especifico en la base de datos
- `GET /api/calendar/status` — indica si el usuario ya conecto su calendario
- Los `access_token` se renuevan automaticamente usando el `refresh_token` guardado, sin requerir que el usuario vuelva a autorizar

## Endpoints

| Metodo | Ruta | Descripcion | Auth |
|---|---|---|---|
| POST | `/api/auth/register` | Crear cuenta | No |
| POST | `/api/auth/login` | Iniciar sesion | No |
| GET | `/api/calendar/connect` | Inicia el flujo OAuth de Google Calendar | Si |
| GET | `/api/calendar/callback` | Callback de Google (recibe el codigo) | No* |
| GET | `/api/calendar/status` | Estado de conexion del calendario | Si |
| GET | `/api/tasks` | Lista las tareas del usuario autenticado | Si |
| POST | `/api/tasks` | Crea una tarea manualmente | Si |
| POST | `/api/tasks/from-text` | Extrae y crea una tarea desde texto libre + sincroniza con Calendar | Si |
| POST | `/api/tasks/voice-command` | Interpreta si el texto es crear o completar una tarea | Si |
| PATCH | `/api/tasks/{id}/toggle-complete` | Marca/desmarca una tarea como completada | Si |
| GET | `/api/tasks/upcoming` | Tareas proximas a vencer (48h), usadas por n8n | Si |
| GET | `/api/tasks/weekly-summary` | Tareas de los ultimos 7 dias, usadas por n8n | Si |

*El callback es publico porque lo invoca Google directamente (no lleva el JWT de la app); la identidad del usuario se transmite via el parametro `state` durante el flujo OAuth.

## Decisiones de diseño

- **OAuth web por usuario, no credencial fija**: la primera version de la integracion con Calendar usaba una unica cuenta de Google autorizada por el desarrollador. Se migro a un flujo OAuth2 completo para aplicaciones web, donde cada usuario autoriza su propia cuenta y sus tokens se guardan de forma aislada en una tabla `google_credentials` relacionada 1:1 con `users`.
- **JWT via query param para flujos de navegador**: el endpoint `/api/calendar/connect` acepta el token tanto por header como por query param, ya que al abrirse desde un navegador externo (no un fetch de la app) no es posible adjuntar headers personalizados de forma confiable en todas las plataformas.
- **Contexto temporal explicito en los prompts**: se inyecta la fecha real del servidor en cada prompt a Groq, evitando que el modelo infiera años incorrectos al interpretar fechas relativas.
- **Un solo endpoint de voz para crear y completar**: `voice-command` le pasa al modelo el listado de tareas pendientes del usuario y deja que la IA decida la intencion.
- **Scope minimo de Google Calendar**: se solicita unicamente `calendar.events`, no acceso total a calendarios.
- **Variables de entorno en vez de credenciales hardcodeadas**: toda credencial se lee desde el entorno de ejecucion, nunca desde el codigo fuente.
- **Migracion de libreria de reconocimiento de voz** (detallado en el repo mobile): se paso de una libreria descontinuada a una activamente mantenida y compatible con builds en la nube.

## Limitaciones conocidas

- **n8n en tiers gratuitos de hosting**: los workflows de automatizacion (recordatorios y resumen semanal) fueron desarrollados y probados exitosamente en local. Al desplegarlos en la nube se encontraron limitaciones de infraestructura en dos proveedores: Railway bloquea trafico SMTP saliente en su tier gratuito, y Render (512MB RAM) resulta insuficiente para correr n8n junto a su base de datos Postgres. Por ahora, n8n se documenta como funcional pero se ejecuta localmente.
- **App de Google Cloud en modo Testing**: mientras el proyecto no pase por el proceso de verificacion de Google (necesario para scopes sensibles como Calendar), solo los emails agregados manualmente como "test users" pueden completar el flujo de autorizacion.
- **Plan gratuito de Render**: el backend se "duerme" tras ~15 min de inactividad y tarda unos segundos en reactivarse con la primera peticion.

## Como correrlo localmente

1. Clona el repo e instala dependencias
2. Copia `application.properties.example` a `application.properties`
3. Exporta las variables de entorno: `DB_PASSWORD`, `GROQ_API_KEY`, `JWT_SECRET`, `GOOGLE_WEB_CLIENT_ID`, `GOOGLE_WEB_CLIENT_SECRET`, `GOOGLE_REDIRECT_URI`
4. Corre con `./mvnw spring-boot:run`

## Proyectos relacionados

- **App Mobile**: https://github.com/Yloaiza/task-manager-mobile
