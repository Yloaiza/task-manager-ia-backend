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

## Comando de voz inteligente

El endpoint `/api/tasks/voice-command` interpreta el texto transcrito por voz y decide automaticamente si el usuario quiere:

- **Crear una tarea nueva**: si el texto describe una tarea nueva (examen, entrega, fecha)
- **Completar una tarea existente**: si el texto indica que ya se hizo algo (ej: "ya hice el examen de calculo")

Para decidir esto, se le pasa a Groq la lista de tareas pendientes del usuario junto con el texto, y el modelo identifica cual tarea coincide semanticamente con lo que el usuario menciono, sin necesidad de que el texto coincida exactamente con el titulo guardado.

## Deploy en produccion

El backend esta desplegado en Render usando Docker: **https://task-manager-ia-backend.onrender.com**

### Decisiones de diseño del deploy

- **Variables de entorno en vez de credenciales hardcodeadas**: `application.properties` usa sintaxis `${VARIABLE:default}` para leer secretos desde el entorno de ejecucion, nunca desde el codigo fuente.
- **Token de Google Calendar en Base64**: el token OAuth de Google (`StoredCredential`) es un archivo binario. Al subirlo directamente a los "Secret Files" de Render (un campo de texto), los bytes no imprimibles se corrompian silenciosamente. La solucion fue codificar el archivo completo en Base64 (texto seguro) como variable de entorno, y decodificarlo de vuelta a binario al arrancar la aplicacion, escribiendolo en una carpeta temporal con permisos de escritura (los Secret Files de Render son de solo lectura).
- **Plan gratuito de Render**: el servicio se "duerme" tras ~15 min de inactividad y tarda unos segundos en reactivarse con la primera peticion.
