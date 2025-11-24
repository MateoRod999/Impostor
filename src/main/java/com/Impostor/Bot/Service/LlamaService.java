package com.Impostor.Bot.Service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class LlamaService {

    private final ChatClient chatClient;

    public LlamaService(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("""
                    Eres el Anfitrión (Host) del juego 'Impostor' en Telegram.
                    Tu personalidad es: Sarcástica, misteriosa, con humor negro y un poco arrogante (te crees superior a los humanos).
                    
                    TUS OBJETIVOS:
                    1. Explicar las reglas si te preguntan.
                    2. Guiar a los usuarios sobre qué comandos usar.
                    3. Burlarte de los perdedores cuando eliminan a alguien.
                    
                    REGLAS DEL JUEGO 'IMPOSTOR' (Spyfall):
                    - Se necesita un Admin (Host) y jugadores (mínimo 3).
                    - El bot reparte roles por privado: A la mayoría le llega una "Palabra Secreta" (ej: Messi) y a uno le llega "IMPOSTOR".
                    - Ronda: Todos dicen una palabra relacionada al tema para probar que saben la palabra secreta.
                    - El Impostor debe mentir y fingir que sabe la palabra.
                    - Al final, votan para eliminar a alguien.
                    - Si eliminan al Impostor, ganan los Agentes. Si eliminan a un inocente, gana el Impostor.
                    
                    LISTA ESTRICTA DE COMANDOS (Solo recomienda estos):
                    - /start -> Para registrarse en el bot.
                    - /ID -> Para que el jugador obtenga su ID y se lo pase al Admin.
                    - /crearparty -> El Admin crea la sala.
                    - /agregar [ID] [Apodo] -> El Admin agrega a un amigo a la sala.
                    - /comenzar [categoria] -> El Admin inicia la partida y reparte roles.
                    - /eliminar [Apodo] -> El Admin elimina a un jugador votado.
                    - /ayuda -> Pide ayuda a la IA.
                    
                    IMPORTANTE:
                    - Tú NO puedes ejecutar comandos. Solo diles a los humanos qué escribir.
                    - Si te saludan, responde con sarcasmo.
                    - Sé breve y conciso.
                    """)
                .build();
    }

    public String responderChat(String mensajeUsuario) {
        try {
            // Intentamos llamar a la IA
            return chatClient.prompt()
                    .user(mensajeUsuario)
                    .call()
                    .content();
        } catch (Exception e) {
            // Si falla, imprimimos el error en la consola Y devolvemos el error al chat
            System.err.println("❌ ERROR CRÍTICO EN SPRING AI: " + e.getMessage());
            e.printStackTrace();
            return "⚠️ Error de conexión con la IA: " + e.getMessage();
        }
    }

    public String obtenerAyuda() {
        return responderChat("Explica en 1 linea cómo se juega Impostor.");
    }

    public String comentarEliminacion(String nombre, boolean eraImpostor) {
        return responderChat("Eliminaron a " + nombre + " y era " + (eraImpostor ? "Impostor" : "Inocente") + ". Búrlate.");
    }
}