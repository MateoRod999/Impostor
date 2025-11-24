package com.Impostor.Bot;

import com.Impostor.Bot.Service.GameService;
import com.Impostor.Bot.Service.LlamaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TelegramBot extends TelegramLongPollingBot {

    @Autowired
    private GameService gameService;

    @Autowired
    private LlamaService aiService;

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Override
    public void onUpdateReceived(Update update) {

        // =================================================
        // 1. MANEJO DE BOTONES (CALLBACKS)
        // =================================================
        if (update.hasCallbackQuery()) {
            String callData = update.getCallbackQuery().getData();
            Long chatId = update.getCallbackQuery().getMessage().getChatId();
            Integer messageId = update.getCallbackQuery().getMessage().getMessageId();
            String callbackId = update.getCallbackQuery().getId();
            String usernameBtn = update.getCallbackQuery().getFrom().getFirstName();

            // --- A. MENÚ DE BIENVENIDA ---
            if (callData.equals("WELCOME:GET_ID")) {
                // Mostramos el ID en una alerta (Pop-up)
                answerCallback(callbackId, "Tu ID es: " + chatId, true);
            }
            else if (callData.equals("WELCOME:CREATE")) {
                // Intentamos crear la party
                String resultado = gameService.crearParty(chatId, usernameBtn);

                if (resultado.contains("⚠️") || resultado.contains("Ya tienes")) {
                    // BLOQUEO: Si ya tiene party, mostramos alerta y NO hacemos nada más
                    answerCallback(callbackId, "⛔ ¡Ya tienes una party activa! Termínala o reiníciala.", true);
                } else {
                    // ÉXITO: Borramos el menú de bienvenida
                    DeleteMessage delete = new DeleteMessage(chatId.toString(), messageId);
                    try { execute(delete); } catch (TelegramApiException e) {}

                    // Mandamos confirmación y mostramos el LOBBY
                    sendMsg(chatId, resultado);
                    enviarLobby(chatId);
                }
            }

            // --- B. LOBBY DE PARTY ---
            else if (callData.equals("LOBBY:REFRESH")) {
                // Actualizamos la lista de jugadores
                String infoNueva = gameService.obtenerInfoParty(chatId);

                EditMessageText edit = new EditMessageText();
                edit.setChatId(chatId.toString());
                edit.setMessageId(messageId);
                edit.setText(infoNueva);
                edit.setParseMode("Markdown");

                // Mantenemos los botones (Corrección del error getReplyMarkup)
                if (update.getCallbackQuery().getMessage() instanceof org.telegram.telegrambots.meta.api.objects.Message) {
                    var msg = (org.telegram.telegrambots.meta.api.objects.Message) update.getCallbackQuery().getMessage();
                    edit.setReplyMarkup(msg.getReplyMarkup());
                }

                try { execute(edit); } catch (TelegramApiException e) { /* Ignorar si el texto es igual */ }
                answerCallback(callbackId, "Lista actualizada");
            }
            else if (callData.equals("LOBBY:ADD_INFO")) {
                // Instrucciones para agregar
                answerCallback(callbackId, "Escribe:\n/agregar [ID] [Apodo]", true);
            }
            else if (callData.equals("LOBBY:START_MENU")) {
                // Borramos el lobby y pasamos a elegir categoría
                DeleteMessage delete = new DeleteMessage(chatId.toString(), messageId);
                try { execute(delete); } catch (TelegramApiException e) {}
                mostrarMenuCategorias(chatId);
            }
// ... dentro del if (update.hasCallbackQuery()) ...
            else if (callData.equals("GAME:REPLAY")) {
                // Borramos el menú anterior
                DeleteMessage delete = new DeleteMessage(chatId.toString(), messageId);
                try { execute(delete); } catch (Exception e) {}

                // Mostramos el Lobby (ahí pueden ver quién está y darle a Comenzar)
                enviarLobby(chatId);
            }

            else if (callData.equals("GAME:END")) {
                // Cerramos la party
                gameService.cerrarParty(chatId);

                EditMessageText edit = new EditMessageText();
                edit.setChatId(chatId.toString());
                edit.setMessageId(messageId);
                edit.setText("👋 **Party Finalizada.**\n¡Gracias por jugar! Usen /start para jugar otra vez.");
                try { execute(edit); } catch (Exception e) {}
            }

            else if (callData.equals("GAME:MODIFY")) {
                // Mostramos el menú de expulsión
                enviarMenuExpulsion(chatId, messageId);
            }

            else if (callData.startsWith("KICK:")) {
                String idStr = callData.split(":")[1];
                Long idKick = Long.parseLong(idStr);

                // Ejecutamos la expulsión
                String nombreExpulsado = gameService.expulsarJugador(chatId, idKick);

                if (nombreExpulsado != null) {
                    answerCallback(callbackId, "Adiós " + nombreExpulsado + " 👋", true);

                    // Avisamos al expulsado (cruel pero justo)
                    try { sendMsg(idKick, "🚫 Has sido eliminado de la party por el administrador."); } catch (Exception e) {}

                    // Actualizamos el menú de expulsión (para sacar el botón del que acabamos de echar)
                    enviarMenuExpulsion(chatId, messageId);
                } else {
                    answerCallback(callbackId, "Error: El jugador ya no está.", true);
                }
            }
            if (callData.startsWith("VOTE:")) {
                // Formato: VOTE:IdSospechoso:IdAdmin
                String[] parts = callData.split(":");
                Long sospechosoId = Long.parseLong(parts[1]);
                Long adminId = Long.parseLong(parts[2]); // Necesitamos saber quién es el admin de la partida
                Long votanteId = chatId; // Quien hizo click

                // Registramos el voto
                String estado = gameService.registrarVoto(adminId, votanteId, sospechosoId);

                if (estado.equals("COMPLETO")) {
                    // 1. Borrar mensaje de votación al usuario
                    DeleteMessage delete = new DeleteMessage(chatId.toString(), messageId);
                    try { execute(delete); } catch (Exception e) {}
                    sendMsg(chatId, "✅ Votación cerrada.");

                    // 2. CALCULAR RESULTADO FINAL
                    String resultadoFinal = gameService.calcularResultadoVotacion(adminId);

                    // 3. Anunciar en el grupo del Admin (o a todos)
                    sendMsg(adminId, "🗳️ **RESULTADOS DE LA VOTACIÓN:**\n\n" + resultadoFinal);

                    // 4. Chiste de la IA (Solo si hubo eliminado)
                    if (resultadoFinal.contains("VICTORIA") || resultadoFinal.contains("GANÓ EL IMPOSTOR") || resultadoFinal.contains("INCORRECTO")) {

                        // Chiste IA (Opcional, lo mandamos antes del menú)
                        String chiste = aiService.responderChat("Comenta este resultado del juego Impostor: " + resultadoFinal);
                        sendMsg(adminId, "🎙️ " + chiste);

                        // ENVIAMOS EL MENÚ DE FIN DE PARTIDA
                        enviarMenuFinPartida(adminId, "🏁 **PARTIDA FINALIZADA**\n\n" + resultadoFinal);

                    } else {
                        // Si hubo EMPATE, no mostramos el menú de fin, solo avisamos que sigue
                        sendMsg(adminId, resultadoFinal);
                    }

                } else {
                    // Simplemente confirmamos el voto y borramos los botones para que no vote doble
                    answerCallback(callbackId, "Votaste a un sospechoso 🔪", false);
                    DeleteMessage delete = new DeleteMessage(chatId.toString(), messageId);
                    try { execute(delete); } catch (Exception e) {}
                    sendMsg(chatId, "✅ Voto registrado. Esperando al resto...");
                }
            }
            else if (callData.equals("ADMIN:START_VOTE")) {
                // 1. Avisamos que se procesó el click
                answerCallback(callbackId, "Iniciando sistema de votación...");

                // 2. Borramos el botón para que no lo apreten dos veces
                // (Opcional: puedes editar el mensaje para que diga "Votación en curso")
                EditMessageText edit = new EditMessageText();
                edit.setChatId(chatId.toString());
                edit.setMessageId(messageId);
                edit.setText("🗳️ **VOTACIÓN EN CURSO**\nRevisen sus chats privados para votar.");
                edit.setParseMode("Markdown");
                try { execute(edit); } catch (Exception e) {}

                // 3. Llamamos al método que ya creaste antes
                iniciarFaseVotacion(chatId);
            }
            // --- C. SELECCIÓN DE CATEGORÍA ---
            else if (callData.startsWith("START:")) {
                String categoria = callData.split(":")[1];

                // Borramos el menú de categorías
                DeleteMessage delete = new DeleteMessage(chatId.toString(), messageId);
                try { execute(delete); } catch (TelegramApiException e) {}

                // Iniciamos la lógica del juego
                iniciarJuegoLogica(chatId, categoria);
            }
            return;
        }

        // =================================================
        // 2. MANEJO DE MENSAJES DE TEXTO
        // =================================================
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();
            String username = update.getMessage().getFrom().getFirstName();

            System.out.println("📩 Mensaje: " + messageText);

            // --- COMANDOS ---

            if (messageText.equals("/start")) {
                // Ahora mostramos el menú con botones
                enviarBienvenida(chatId, username);
            }
// ... otros comandos ...
            else if (messageText.equals("/votar")) {
                iniciarFaseVotacion(chatId); // chatId aquí es el Admin
            }
            else if (messageText.equalsIgnoreCase("/ID")) {
                sendMsg(chatId, "Tu ID es: `" + chatId + "`");
            }

            else if (messageText.equals("/crearparty")) {
                String res = gameService.crearParty(chatId, username);
                sendMsg(chatId, res);
                if (!res.contains("⚠️")) {
                    enviarLobby(chatId); // Mostramos el Dashboard si se creó bien
                }
            }

            else if (messageText.equals("/party")) {
                enviarLobby(chatId); // Comando directo para ver el dashboard
            }

            else if (messageText.startsWith("/agregar")) {
                String[] parts = messageText.split(" ", 3);
                if(parts.length == 3) {
                    try {
                        Long pid = Long.parseLong(parts[1]);
                        String res = gameService.agregarJugador(chatId, pid, parts[2]);
                        sendMsg(chatId, res);

                        // Si agregó con éxito, mostramos el lobby actualizado automáticamente
                        if (res.contains("agregado")) {
                            enviarLobby(chatId);
                            // Intentamos avisar al jugador (opcional)
                            try { sendMsg(pid, "👋 " + username + " te agregó a su party."); } catch (Exception e) {}
                        }
                    } catch(Exception e) { sendMsg(chatId, "❌ Error: El ID debe ser numérico."); }
                } else {
                    sendMsg(chatId, "❌ Uso: `/agregar [ID] [Apodo]`");
                }
            }

            else if (messageText.startsWith("/comenzar")) {
                // Si escribe directo "/comenzar futbol"
                String[] parts = messageText.split(" ");
                if (parts.length > 1) {
                    iniciarJuegoLogica(chatId, parts[1]);
                } else {
                    // Si solo pone "/comenzar", mostramos botones
                    mostrarMenuCategorias(chatId);
                }
            }

            else if (messageText.startsWith("/eliminar")) {
                String[] parts = messageText.split(" ", 2);
                if (parts.length == 2) {
                    String res = gameService.procesarEliminacion(chatId, parts[1]);
                    sendMsg(chatId, res);

                    // IA Comentarista
                    if (!res.startsWith("❌") && !res.startsWith("⚠️")) {
                        boolean eraImpostor = res.contains("ERA EL IMPOSTOR");
                        String chiste = aiService.comentarEliminacion(parts[1], eraImpostor);
                        sendMsg(chatId, "🎙️ " + chiste);
                    }
                }
            }

            else if (messageText.equals("/ayuda")) {
                String ayuda = aiService.obtenerAyuda();
                sendMsg(chatId, ayuda);
            }

            // --- MODO CHARLA (SI NO ES COMANDO) ---
            else {
                // Efecto "Escribiendo..."
                try {
                    SendChatAction action = new SendChatAction();
                    action.setChatId(chatId.toString());
                    action.setAction(ActionType.TYPING);
                    execute(action);
                } catch (Exception e) {}

                // Respuesta de la IA
                String respuesta = aiService.responderChat(messageText);
                sendMsg(chatId, respuesta);
            }
        }
    }

    // Helper para responder a los botones (quitar el relojito de carga)
    private void answerCallback(String callbackId, String text, boolean showAlert) {
        org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery answer =
                new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery();
        answer.setCallbackQueryId(callbackId);
        answer.setText(text);
        answer.setShowAlert(showAlert); // true = ventana emergente, false = notificación pequeña
        try { execute(answer); } catch (TelegramApiException e) {}
    }

    // Helper sobrecargado simple
    private void answerCallback(String callbackId, String text) {
        answerCallback(callbackId, text, false);
    }

    // --- MÉTODO NUEVO: MUESTRA EL LOBBY INTERACTIVO ---
    private void enviarLobby(Long chatId) {
        // 1. Obtenemos el texto de la lista de jugadores
        String infoParty = gameService.obtenerInfoParty(chatId);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(infoParty);
        message.setParseMode("Markdown");

        // 2. Creamos los botones
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // FILA 1: Botón de Ayuda para Agregar y Botón de Actualizar
        List<InlineKeyboardButton> row1 = new ArrayList<>();

        var btnAdd = new InlineKeyboardButton();
        btnAdd.setText("➕ Agregar Jugador");
        btnAdd.setCallbackData("LOBBY:ADD_INFO"); // Muestra cómo agregar

        var btnRefresh = new InlineKeyboardButton();
        btnRefresh.setText("🔄 Actualizar Lista");
        btnRefresh.setCallbackData("LOBBY:REFRESH"); // Recarga la lista

        row1.add(btnAdd);
        row1.add(btnRefresh);
        rows.add(row1);

        // FILA 2: Botón GRANDE de COMENZAR
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        var btnStart = new InlineKeyboardButton();
        btnStart.setText("🚀 COMENZAR PARTIDA");
        btnStart.setCallbackData("LOBBY:START_MENU");
        row2.add(btnStart);
        rows.add(row2);

        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);

        try { execute(message); } catch (TelegramApiException e) { e.printStackTrace(); }
    }
    private void iniciarFaseVotacion(Long adminId) {
        // Obtenemos los jugadores vivos
        Map<Long, String> vivos = gameService.obtenerJugadoresVivos(adminId);

        if (vivos.size() < 2) {
            sendMsg(adminId, "❌ No hay suficientes jugadores vivos para votar.");
            return;
        }

        sendMsg(adminId, "🗳️ **ABRIENDO VOTACIÓN** 🗳️\nEnviando menú a todos los jugadores...");

        // Construimos el teclado UNA vez
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Map.Entry<Long, String> entry : vivos.entrySet()) {
            Long idSospechoso = entry.getKey();
            String nombre = entry.getValue();

            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText("🔪 " + nombre);
            // Guardamos el ID del sospechoso Y el ID del Admin en el callback para encontrar la sesión
            btn.setCallbackData("VOTE:" + idSospechoso + ":" + adminId);
            row.add(btn);
            rows.add(row);
        }
        markup.setKeyboard(rows);

        // Enviamos el mensaje a CADA jugador vivo
        for (Long idJugador : vivos.keySet()) {
            SendMessage msg = new SendMessage();
            msg.setChatId(idJugador.toString());
            msg.setText("🚨 **¡MOMENTO DE VOTAR!** 🚨\n¿Quién crees que es el Impostor?");
            msg.setReplyMarkup(markup);
            try { execute(msg); } catch (Exception e) {
                sendMsg(adminId, "⚠️ No pude enviar voto a: " + vivos.get(idJugador));
            }
        }
    }
    private void mostrarMenuCategorias(Long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("📂 **Elige una categoría para la partida:**");
        message.setParseMode("Markdown");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Obtenemos categorías del JSON
        List<String> categorias = gameService.getCategoriasDisponibles();

        // Agregamos botón RANDOM al principio
        List<InlineKeyboardButton> rowRandom = new ArrayList<>();
        var btnRandom = new InlineKeyboardButton();
        btnRandom.setText("🎲 Aleatorio");
        btnRandom.setCallbackData("START:random");
        rowRandom.add(btnRandom);
        rows.add(rowRandom);

        // Agregamos una fila por cada categoría
        for (String cat : categorias) {
            List<InlineKeyboardButton> row = new ArrayList<>();
            var btn = new InlineKeyboardButton();
            btn.setText("📁 " + cat.toUpperCase());
            btn.setCallbackData("START:" + cat);
            row.add(btn);
            rows.add(row);
        }

        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);

        try { execute(message); } catch (TelegramApiException e) { e.printStackTrace(); }
    }

    private void iniciarJuegoLogica(Long adminChatId, String categoria) {
        Map<Long, String> roles = gameService.comenzarJuego(adminChatId, categoria);

        if (roles != null) {
            StringBuilder errores = new StringBuilder();

            // 1. Repartir roles (Código que ya tenías)
            for (Map.Entry<Long, String> entry : roles.entrySet()) {
                Long idJugador = entry.getKey();
                String textoRol = entry.getValue();
                try {
                    SendMessage message = new SendMessage();
                    message.setChatId(idJugador.toString());
                    message.setText(textoRol);
                    message.setParseMode("Markdown");
                    execute(message);
                } catch (TelegramApiException e) {
                    errores.append("\n- ID ").append(idJugador).append(" (No inició el bot)");
                }
            }

            // 2. Construir mensaje final para el Admin
            String reporteFinal = "🎮 **¡JUEGO INICIADO!**\n" +
                    "Categoría: " + categoria.toUpperCase() + "\n\n" +
                    "🗣️ _¡Que comience el debate!_\n" +
                    "Cuando estén listos para eliminar a alguien, presiona el botón.";

            if (errores.length() > 0) {
                reporteFinal += "\n\n⚠️ **Error:** No pude escribirle a:" + errores.toString();
            }

            // 3. Crear el mensaje con el botón "INICIAR VOTACIÓN"
            SendMessage msgAdmin = new SendMessage();
            msgAdmin.setChatId(adminChatId.toString());
            msgAdmin.setText(reporteFinal);
            msgAdmin.setParseMode("Markdown");

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();

            var btnVotar = new InlineKeyboardButton();
            btnVotar.setText("🗳️ INICIAR VOTACIÓN");
            btnVotar.setCallbackData("ADMIN:START_VOTE"); // Nuevo Callback

            row.add(btnVotar);
            rows.add(row);
            markup.setKeyboard(rows);
            msgAdmin.setReplyMarkup(markup);

            try { execute(msgAdmin); } catch (TelegramApiException e) { e.printStackTrace(); }

        } else {
            sendMsg(adminChatId, "❌ Error: Faltan jugadores (mínimo 3) o no creaste la party.");
        }
    }
    private void sendMsg(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setParseMode("Markdown");
        try { execute(message); } catch (TelegramApiException e) { e.printStackTrace(); }
    }
    private void enviarBienvenida(Long chatId, String username) {
        String texto = "🕵️‍♂️ **IMPOSTOR BOT** 🕵️‍♀️\n" +
                "Bienvenido, " + username + ".\n\n" +
                "Aquí pondremos a prueba tu capacidad de mentir.\n\n" +
                "👇 **¿QUÉ DESEAS HACER?**\n" +
                "• Si te invitaron a jugar, obtén tu ID y pásaselo al Admin.\n" +
                "• Si eres el organizador, crea la sala aquí mismo.";

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(texto);
        message.setParseMode("Markdown");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // BOTÓN 1: OBTENER ID
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        var btnId = new InlineKeyboardButton();
        btnId.setText("🆔 Obtener mi ID");
        btnId.setCallbackData("WELCOME:GET_ID");
        row1.add(btnId);
        rows.add(row1);

        // BOTÓN 2: CREAR PARTY
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        var btnCreate = new InlineKeyboardButton();
        btnCreate.setText("🎮 Crear Nueva Party");
        btnCreate.setCallbackData("WELCOME:CREATE");
        row2.add(btnCreate);
        rows.add(row2);

        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);

        try { execute(message); } catch (TelegramApiException e) { e.printStackTrace(); }
    }
    private void enviarMenuFinPartida(Long chatId, String textoResultado) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(textoResultado + "\n\n👇 **¿Qué hacemos ahora?**");
        message.setParseMode("Markdown");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Fila 1: Jugar de Nuevo
        var btnReplay = new InlineKeyboardButton();
        btnReplay.setText("🔄 Jugar de Nuevo");
        btnReplay.setCallbackData("GAME:REPLAY");
        rows.add(List.of(btnReplay));

        // Fila 2: Modificar Party y Finalizar
        List<InlineKeyboardButton> row2 = new ArrayList<>();

        var btnMod = new InlineKeyboardButton();
        btnMod.setText("⚙️ Modificar Party (Kick)");
        btnMod.setCallbackData("GAME:MODIFY");

        var btnEnd = new InlineKeyboardButton();
        btnEnd.setText("❌ Finalizar Party");
        btnEnd.setCallbackData("GAME:END");

        row2.add(btnMod);
        row2.add(btnEnd);
        rows.add(row2);

        markup.setKeyboard(rows);
        message.setReplyMarkup(markup);

        try { execute(message); } catch (TelegramApiException e) { e.printStackTrace(); }
    }
    private void enviarMenuExpulsion(Long chatId, Integer messageId) {
        // Obtenemos la sesión actual para ver los jugadores
        String info = "🗑️ **SELECCIONA A QUIÉN ELIMINAR DE LA PARTY:**";

        // Usamos el servicio para obtener el mapa de jugadores
        // (Truco: usamos obtenerInfoParty para validar, pero aquí accedemos directo al mapa si es posible,
        // o mejor, modificamos GameService para que nos de el mapa.
        // Para hacerlo simple aquí, asumiremos que GameService tiene un getter público de la sesión o creamos un método rápido).
        // ⚠️ NOTA: Agregaremos un método 'obtenerMapaJugadores' en GameService abajo para esto.

        Map<Long, String> jugadores = gameService.obtenerMapaJugadores(chatId);

        EditMessageText edit = new EditMessageText();
        edit.setChatId(chatId.toString());
        edit.setMessageId(messageId);
        edit.setText(info);
        edit.setParseMode("Markdown");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        if (jugadores != null) {
            for (Map.Entry<Long, String> entry : jugadores.entrySet()) {
                Long id = entry.getKey();
                // No mostramos al Admin (no se puede auto-kickear)
                if (id.equals(chatId)) continue;

                var btn = new InlineKeyboardButton();
                btn.setText("🚫 Expulsar a " + entry.getValue());
                btn.setCallbackData("KICK:" + id);
                rows.add(List.of(btn));
            }
        }

        // Botón Volver
        var btnBack = new InlineKeyboardButton();
        btnBack.setText("⬅️ Volver al Menú");
        btnBack.setCallbackData("GAME:REPLAY"); // Volver al lobby/replay
        rows.add(List.of(btnBack));

        markup.setKeyboard(rows);
        edit.setReplyMarkup(markup);

        try { execute(edit); } catch (TelegramApiException e) { e.printStackTrace(); }
    }

    @Override
    public String getBotUsername() { return botUsername; }
    @Override
    public String getBotToken() { return botToken; }
}