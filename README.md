# Task Manager IA

Backend de un asistente de tareas académicas que convierte texto libre en eventos estructurados usando IA, y los sincroniza automáticamente con Google Calendar.

## Cómo funciona

Envías un texto como *"Tengo examen de cálculo el 20 de julio, es un tema difícil"* y el sistema:

1. Extrae automáticamente título, materia, fecha y dificultad usando **function calling** con la API de Groq (Llama 3.3 70B)
2. Guarda la tarea estructurada en una base de datos PostgreSQL (Supabase)
3. Crea el evento correspondiente en Google Calendar mediante OAuth2

## Stack

- **Backend**: Java 17 + Spring Boot 4
- **Base de datos**: PostgreSQL (Supabase, conexión vía Session Pooler / IPv4)
- **IA**: Groq API (Llama 3.3 70B) con function calling
- **Calendario**: Google Calendar API (OAuth2, scope `calendar.events`)

## Decisiones de diseño

- **Contexto temporal explícito**: el prompt del sistema incluye la fecha actual del servidor, evitando que el modelo "invente" años incorrectos al interpretar fechas relativas.
- **Scope mínimo de Google Calendar**: se solicita únicamente `calendar.events` (crear/editar eventos), no acceso total a calendarios, siguiendo el principio de menor privilegio.
- **Conexión IPv4 gratuita a Supabase**: se usa el Session Pooler en vez de conexión directa, evitando el costo del add-on de IPv4 dedicado.

## Cómo correrlo localmente

1. Cloná el repo
2. Copiá `application.properties.example` a `application.properties` y completá tus credenciales
3. Descargá tus credenciales de Google Cloud (OAuth Desktop app) como `google-credentials.json` en `src/main/resources/`
4. Corré con `./mvnw spring-boot:run`

## Próximos pasos

- App mobile con Expo/React Native (entrada por voz)
- Automatizaciones con n8n (recordatorios, resúmenes semanales)
- Notificaciones push con Firebase

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/tasks` | Lista todas las tareas |
| POST | `/api/tasks` | Crea una tarea manualmente |
| POST | `/api/tasks/from-text` | Extrae y crea una tarea desde texto libre + sincroniza con Calendar |
