# 🕵️ Impostor Bot - Telegram Game

Bot de Telegram para jugar al juego social "Impostor" (inspirado en Spyfall). Un jugador secreto es el impostor que debe adivinar una palabra secreta mientras los demás agentes intentan descubrirlo.

## 🎮 ¿Cómo se juega?

1. **Setup**: Un administrador crea una party e invita jugadores (mínimo 3)
2. **Roles secretos**: El bot asigna aleatoriamente:
   - **Agentes**: Reciben una palabra secreta (ej: "Lionel Messi")
   - **Impostor**: No conoce la palabra y debe fingir que sí
3. **Ronda de discusión**: Todos dicen palabras relacionadas al tema para demostrar que conocen la palabra secreta
4. **Votación**: Los jugadores votan para eliminar al sospechoso
5. **Victoria**:
   - **Agentes ganan**: Si eliminan al impostor
   - **Impostor gana**: Si eliminan a todos los agentes sin ser descubierto

## 🚀 Características

- ✅ Sistema de parties multijugador
- 🎲 Selección aleatoria de impostor y palabras
- 📂 Múltiples categorías (Fútbol, Animales, etc.)
- 🗳️ Sistema de votación integrado con botones interactivos
- 🤖 Asistente IA con personalidad (basado en Llama 3.1)
- 📊 Dashboard interactivo para gestionar jugadores
- 🔄 Opción de replay y modificación de parties

## 🛠️ Tecnologías

- **Java 21** + **Spring Boot 3.5.8**
- **Telegram Bots API** (telegrambots-spring-boot-starter)
- **Spring AI** + **Groq API** (Llama 3.1-8b-instant)
- **Maven** para gestión de dependencias
- **Docker** ready para deployment

## 📋 Requisitos previos

- Java 21 o superior
- Maven 3.9+
- Cuenta de Telegram y bot creado con [@BotFather](https://t.me/botfather)
- API Key de Groq (para el asistente IA)

## ⚙️ Instalación

### 1. Clonar el repositorio

```bash
git clone <tu-repositorio>
cd Bot
```

### 2. Configurar variables de entorno

Crea un archivo `.env` o configura las siguientes variables:

```properties
TELEGRAM_BOT_USERNAME=tu_bot_username
TELEGRAM_BOT_TOKEN=tu_bot_token_de_botfather
GROQ_API_KEY=tu_groq_api_key
```

### 3. Compilar y ejecutar

#### Opción A: Con Maven Wrapper (recomendado)

```bash
./mvnw clean package
java -jar target/Bot-0.0.1-SNAPSHOT.jar
```

#### Opción B: Con Docker

```bash
docker build -t impostor-bot .
docker run -p 8080:8080 \
  -e TELEGRAM_BOT_USERNAME=tu_username \
  -e TELEGRAM_BOT_TOKEN=tu_token \
  -e GROQ_API_KEY=tu_api_key \
  impostor-bot
```

## 📱 Comandos del Bot

### Comandos generales

- `/start` o `/menu` - Menú principal interactivo
- `/ID` - Obtener tu ID de jugador
- `/ayuda` - Ayuda sobre el juego

### Comandos del Administrador

- `/crearparty` - Crear una nueva party
- `/agregar [ID] [Apodo]` - Agregar un jugador a la party
- `/party` - Ver y gestionar el lobby actual
- `/comenzar [categoria]` - Iniciar el juego (o elegir categoría con botones)
- `/eliminarparty` - Cerrar la party actual

### Comandos de jugador

- `/salirparty` - Abandonar la party actual
- `/votar` - Iniciar fase de votación (solo admin)

### Charla con IA

Simplemente escribe cualquier mensaje sin "/" y el bot responderá con su personalidad basada en streamers argentinos.

## 🎯 Flujo de juego típico

1. **Admin**: `/crearparty`
2. **Jugadores**: Envían `/ID` al admin
3. **Admin**: `/agregar 123456789 Jugador1` (para cada jugador)
4. **Admin**: Presiona "🚀 COMENZAR" en el dashboard
5. **Admin**: Selecciona categoría (o aleatorio)
6. **Todos**: Reciben su rol en privado
7. **Ronda**: Discuten y dicen palabras relacionadas
8. **Admin**: Presiona "🗳️ INICIAR VOTACIÓN"
9. **Todos**: Votan al sospechoso desde su chat privado
10. **Sistema**: Calcula automáticamente resultado y continúa o finaliza

## 📂 Estructura del proyecto

```
src/main/java/com/Impostor/Bot/
├── BotApplication.java          # Punto de entrada
├── BotInitializer.java          # Configuración del bot
├── TelegramBot.java             # Lógica principal del bot
├── GameSession.java             # Modelo de sesión de juego
├── Service/
│   ├── GameService.java         # Lógica del juego
│   └── LlamaService.java        # Integración con IA
└── controller/
    └── PingController.java      # Endpoint de health check

src/main/resources/
├── application.properties       # Configuración
└── words.json                   # Base de datos de palabras por categoría
```

## 🔧 Personalización

### Agregar nuevas categorías

Edita `src/main/resources/words.json`:

```json
{
  "categorias": [
    {
      "nombre": "tu_categoria",
      "palabras": ["palabra1", "palabra2", "palabra3"]
    }
  ]
}
```

### Modificar personalidad de la IA

Edita el `defaultSystem` en `LlamaService.java`:

```java
this.chatClient = builder
    .defaultSystem("""
        Tu nueva personalidad aquí...
    """)
    .build();
```

## 🐛 Troubleshooting

### El bot no responde

- Verifica que las variables de entorno estén configuradas correctamente
- Revisa los logs: `logging.level.com.Impostor.Bot=DEBUG`
- Asegúrate de que el bot esté iniciado con `/start`

### La IA no funciona

- Verifica tu API key de Groq
- Revisa la URL base: `https://api.groq.com/openai`
- Comprueba límites de rate en tu cuenta de Groq

### Error al cargar palabras

- Asegúrate de que `words.json` esté en `src/main/resources/`
- Verifica que el JSON sea válido (sin comas extras)

## 🚀 Deployment

### Render / Railway / Heroku

1. Configura las variables de entorno en el dashboard
2. Conecta tu repositorio
3. El `Dockerfile` incluido se encargará del build automático
4. Endpoint de health check disponible en `/ping`

### Keep-alive (opcional)

Para evitar que servicios gratuitos se duerman, puedes usar servicios como [UptimeRobot](https://uptimerobot.com/) apuntando a `https://tu-app.com/ping`

## 📝 Notas importantes

- **Mínimo 3 jugadores** para iniciar una partida
- Los jugadores deben haber iniciado el bot con `/start` antes de ser agregados
- El admin no puede auto-expulsarse de su propia party
- Las votaciones requieren que **todos los jugadores vivos** voten
- En caso de empate, se vuelve a votar automáticamente

## 🤝 Contribuciones

¡Las contribuciones son bienvenidas! Por favor:

1. Fork el proyecto
2. Crea una rama para tu feature (`git checkout -b feature/nueva-categoria`)
3. Commit tus cambios (`git commit -m 'Agregar nueva categoría'`)
4. Push a la rama (`git push origin feature/nueva-categoria`)
5. Abre un Pull Request

## 📄 Licencia

Este proyecto es de código abierto y está disponible bajo la licencia que elijas agregar.

## 👨‍💻 Autor

**Mateo Rodriguez**

## 🎉 Agradecimientos

- A La Cobra y Davo Xeneize por la inspiración del bot
- A la comunidad de Spring Boot y Telegram Bots
- A todos los que contribuyen con nuevas categorías de palabras

---

**¿Encontraste un bug?** Abre un issue en GitHub
**¿Tienes preguntas?** Pregúntale directamente al bot, tiene IA 🤖