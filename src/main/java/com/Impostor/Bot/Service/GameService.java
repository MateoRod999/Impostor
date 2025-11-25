package com.Impostor.Bot.Service;

import com.Impostor.Bot.GameSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GameService {

    // Mapa de sesiones activas (AdminID -> Sesión)
    private final Map<Long, GameSession> partidasActivas = new ConcurrentHashMap<>();

    // Mapa de categorías cargado desde el JSON
    private final Map<String, List<String>> baseDeDatosPalabras = new HashMap<>();
    private final List<String> listaCategorias = new ArrayList<>();

    @PostConstruct
    public void cargarPalabras() {
        try {
            // 1. Buscamos el archivo como recurso (igual que en SilverSorgo)
            ClassPathResource resource = new ClassPathResource("words.json");

            // 2. Abrimos el flujo de datos (InputStream)
            // El 'try' entre paréntesis asegura que se cierre solo al terminar
            try (InputStream inputStream = resource.getInputStream()) {

                ObjectMapper mapper = new ObjectMapper();

                // 3. Leemos el árbol JSON directamente del Stream
                JsonNode root = mapper.readTree(inputStream);
                JsonNode categorias = root.get("categorias");

                if (categorias.isArray()) {
                    for (JsonNode cat : categorias) {
                        String nombreCat = cat.get("nombre").asText().toLowerCase();
                        List<String> palabras = new ArrayList<>();
                        cat.get("palabras").forEach(p -> palabras.add(p.asText()));

                        baseDeDatosPalabras.put(nombreCat, palabras);
                        listaCategorias.add(nombreCat);
                    }
                }
                System.out.println(">>> ¡Palabras cargadas! Categorías: " + listaCategorias);
            }

        } catch (IOException e) {
            System.err.println("!!! ERROR AL CARGAR EL words.json !!!");
            // Imprimimos el error real para verlo en los logs de Render
            e.printStackTrace();
        }
    }

    // --- GESTIÓN DE PARTIDAS ---

    public String crearParty(Long adminId, String adminName) {
        if (partidasActivas.containsKey(adminId)) {
            return "⚠️ Ya tienes una partida activa. Termínala o reiníciala.";
        }
        GameSession session = new GameSession(adminId, adminName);
        partidasActivas.put(adminId, session);
        return "✅ **Party Creada**\nEres el Admin. Pide a tus amigos su /ID y agrégalos con:\n`/agregar [ID] [Apodo]`";
    }


    public String agregarJugador(Long adminId, Long jugadorId, String apodo) {
        GameSession session = partidasActivas.get(adminId);
        if (session == null) return "❌ No has creado una party.";
        if (session.isEnJuego()) return "❌ La partida ya empezó.";

        session.getJugadores().put(jugadorId, apodo);
        return "✅ Jugador " + apodo + " agregado.";
    }

    // --- LÓGICA DE JUEGO ---

    public Map<Long, String> comenzarJuego(Long adminId, String categoriaPreferida) {
        GameSession session = partidasActivas.get(adminId);

        // ⚠️ RECUERDA: Pon < 3 para jugar con gente real, o < 1 para pruebas solo
        if (session == null || session.getJugadores().size() < 3) {
            return null;
        }

        // 1. Limpiamos la sesión por si acaso venimos de un replay sucio
        session.reiniciarSesion();

        // 2. Elegir Categoría y Palabra (Igual que antes...)
        String categoriaUsar = categoriaPreferida.toLowerCase();
        if (categoriaPreferida.equals("random") || !baseDeDatosPalabras.containsKey(categoriaUsar)) {
            categoriaUsar = listaCategorias.get(new Random().nextInt(listaCategorias.size()));
        }
        List<String> palabras = baseDeDatosPalabras.get(categoriaUsar);
        String palabra = palabras.get(new Random().nextInt(palabras.size()));

        // 3. ELEGIR IMPOSTOR (MEJORADO CON SHUFFLE) 🎲
        List<Long> ids = new ArrayList<>(session.getJugadores().keySet());
        Collections.shuffle(ids); // <--- ESTO MEZCLA LA LISTA SIEMPRE

        Long idImpostor = ids.get(0); // Tomamos el primero de la lista ya mezclada

        // 4. Guardar estado
        session.iniciarRonda(idImpostor, palabra, categoriaUsar);

        // 5. Retornar mensajes...
        Map<Long, String> mensajesAEnviar = new HashMap<>();
        for (Long id : ids) {
            if (id.equals(idImpostor)) {
                mensajesAEnviar.put(id, "🤫 **ERES EL IMPOSTOR** 🤫\nCategoría: " + categoriaUsar.toUpperCase() + "\nTu objetivo: Pasar desapercibido.");
            } else {
                mensajesAEnviar.put(id, "🕵️ Eres un Agente.\nCategoría: " + categoriaUsar.toUpperCase() + "\nLa palabra secreta es: **" + palabra + "**");
            }
        }
        return mensajesAEnviar;
    }
    public boolean reiniciarPartida(Long adminId) {
        GameSession session = partidasActivas.get(adminId);
        if (session != null) {
            session.reiniciarSesion(); // Limpia muertos y votos
            return true;
        }
        return false;
    }
    public List <String> getCategoriasDisponibles(){
        return listaCategorias;
    }
    // Devuelve un mapa ID->Nombre solo de los vivos (para los botones)
    public Map<Long, String> obtenerJugadoresVivos(Long adminId) {
        GameSession session = partidasActivas.get(adminId);
        if (session == null) return new HashMap<>();

        Map<Long, String> vivos = new HashMap<>();
        for (Long id : session.getJugadoresVivos()) {
            vivos.put(id, session.getJugadores().get(id));
        }
        return vivos;
    }

    public String registrarVoto(Long adminId, Long votanteId, Long sospechosoId) {
        GameSession session = partidasActivas.get(adminId);
        if (session == null) return "Error de sesión.";

        // Registramos el voto
        session.registrarVoto(votanteId, sospechosoId);

        // Verificamos si ya votaron todos los vivos
        int totalVivos = session.getJugadoresVivos().size();
        int votosTotales = session.getCantidadVotos();

        if (votosTotales >= totalVivos) {
            return "COMPLETO"; // Señal para calcular el resultado
        }

        return "Voto registrado (" + votosTotales + "/" + totalVivos + ")";
    }

    public String calcularResultadoVotacion(Long adminId) {
        GameSession session = partidasActivas.get(adminId);
        if (session == null) return "ERROR";

        Map<Long, Long> votos = session.getVotosActuales();

        // 1. Contar votos
        Map<Long, Integer> conteo = new HashMap<>();
        for (Long sospechoso : votos.values()) {
            conteo.put(sospechoso, conteo.getOrDefault(sospechoso, 0) + 1);
        }

        // 2. Buscar al más votado
        Long masVotado = null;
        int maxVotos = -1;
        boolean empate = false;

        for (Map.Entry<Long, Integer> entry : conteo.entrySet()) {
            if (entry.getValue() > maxVotos) {
                maxVotos = entry.getValue();
                masVotado = entry.getKey();
                empate = false;
            } else if (entry.getValue() == maxVotos) {
                empate = true; // Detectamos empate
            }
        }

        // Limpiamos los votos SIEMPRE para la siguiente ronda (o revotación)
        session.limpiarVotos();

        // 3. SI HAY EMPATE O NADIE VOTÓ -> REVOTACIÓN
        if (empate || masVotado == null) {
            return "REVOTE"; // Señal para el bot: "Manda los botones de nuevo"
        }

        // 4. Si hay un ganador del voto, lo eliminamos
        String nombreEliminado = session.getJugadores().get(masVotado);
        return procesarEliminacion(adminId, nombreEliminado);
    }

    public GameSession obtenerSesion(Long adminId) {
        return partidasActivas.get(adminId);
    }
    public String salirDeParty(Long jugadorId) {
        // Buscamos en todas las partidas activas
        for (Map.Entry<Long, GameSession> entry : partidasActivas.entrySet()) {
            GameSession session = entry.getValue();

            // Si el jugador está en esta party
            if (session.getJugadores().containsKey(jugadorId)) {

                // CASO A: Es el Admin
                if (session.getAdminId().equals(jugadorId)) {
                    partidasActivas.remove(jugadorId);
                    return "ADMIN_CLOSED"; // Si el admin sale, se borra la party
                }

                // CASO B: Es un jugador normal
                String apodo = session.getJugadores().get(jugadorId);
                session.getJugadores().remove(jugadorId);
                session.eliminarJugador(jugadorId); // Lo sacamos de vivos también

                return "LEFT:" + session.getAdminId() + ":" + apodo; // Devolvemos ID del admin para avisarle
            }
        }
        return "NOT_FOUND";
    }

    public String obtenerInfoPartyInteligente(Long usuarioId) {
        GameSession session = null;

        // Primero revisamos si es Admin
        if (partidasActivas.containsKey(usuarioId)) {
            session = partidasActivas.get(usuarioId);
        } else {
            // Si no, buscamos si es participante en alguna
            for (GameSession s : partidasActivas.values()) {
                if (s.getJugadores().containsKey(usuarioId)) {
                    session = s;
                    break;
                }
            }
        }

        if (session == null) return null; // No está en ninguna party

        // Construimos el texto (reutilizando lógica o copiando el formato)
        StringBuilder sb = new StringBuilder("📋 **LOBBY DE LA PARTY**\nAdmin: " + session.getJugadores().get(session.getAdminId()) + "\n\n");
        int i = 1;
        for (String nombre : session.getJugadores().values()) {
            String nombreLimpio = nombre.replace("_", "\\_").replace("*", "\\*").replace("`", "\\`");
            sb.append(i++).append(". ").append(nombre).append("\n");
        }
        sb.append("\n👥 Total: ").append(session.getJugadores().size());

        // Retornamos también si el que pide es Admin o no (truco para los botones)
        boolean esAdmin = session.getAdminId().equals(usuarioId);
        return (esAdmin ? "ROLE:ADMIN" : "ROLE:PLAYER") + "||" + sb.toString();
    }

    // 3. Eliminar party por ID de Admin (Wrapper simple)
    public void eliminarParty(Long adminId) {
        partidasActivas.remove(adminId);
    }
    public String obtenerInfoParty(Long adminId) {
        GameSession session = partidasActivas.get(adminId);

        if (session == null) {
            return "❌ No tienes una party creada.\nUsa `/crearparty` para empezar.";
        }

        Map<Long, String> jugadores = session.getJugadores();
        StringBuilder sb = new StringBuilder("📋 **INTEGRANTES DE LA PARTY:**\n\n");

        int i = 1;
        for (Map.Entry<Long, String> entry : jugadores.entrySet()) {
            Long id = entry.getKey();
            String apodo = entry.getValue();

            sb.append(i).append(". **").append(apodo).append("**");

            // Marcamos quién es el admin
            if (id.equals(adminId)) {
                sb.append(" (Admin 👑)");
            }

            // (Opcional) Si quieres mostrar el ID también:
            // sb.append(" [`").append(id).append("`]");

            sb.append("\n");
            i++;
        }

        sb.append("\n👥 Total: ").append(jugadores.size()).append(" jugadores.");

        if (jugadores.size() < 3) {
            sb.append("\n⚠️ _Faltan al menos ").append(3 - jugadores.size()).append(" para poder iniciar._");
        } else {
            sb.append("\n✅ _¡Listos para comenzar!_");
        }

        return sb.toString();
    }
    public void cerrarParty(Long adminId) {
        partidasActivas.remove(adminId);
    }
    public Map<Long, String> obtenerMapaJugadores(Long adminId) {
        GameSession session = partidasActivas.get(adminId);
        return (session != null) ? session.getJugadores() : null;
    }
    public String expulsarJugador(Long adminId, Long idJugadorAExpulsar) {
        GameSession session = partidasActivas.get(adminId);
        if (session == null) return "Error: No hay party.";

        String nombre = session.getJugadores().get(idJugadorAExpulsar);
        if (nombre != null) {
            session.getJugadores().remove(idJugadorAExpulsar);
            // También lo sacamos de vivos por si acaso
            session.eliminarJugador(idJugadorAExpulsar);
            return nombre; // Devolvemos el nombre para confirmar
        }
        return null;
    }
    public String procesarEliminacion(Long adminId, String apodoEliminado) {
        GameSession session = partidasActivas.get(adminId);
        if (session == null || !session.isEnJuego()) return "⚠️ No hay juego activo.";

        Long idEliminado = session.buscarIdPorApodo(apodoEliminado);
        if (idEliminado == null) return "❌ No encontré el apodo: " + apodoEliminado;

        // 1. Eliminar al jugador de VIVOS
        session.eliminarJugador(idEliminado);

        boolean eraImpostor = session.esImpostor(idEliminado);
        int vivos = session.getJugadoresVivos().size();

        // 2. LÓGICA DE VICTORIA/DERROTA
        if (eraImpostor) {
            session.setEnJuego(false);
            return "VICTORIA_AGENTES|" + apodoEliminado;
        } else {
            // Si el eliminado NO era impostor
            // REGLA: Gana Impostor si quedan 2 personas (1 vs 1)
            if (vivos <= 2) {
                session.setEnJuego(false);

                // --- CORRECCIÓN DEL BUG NULL ---
                Long impId = session.getImpostorId();
                String nombreImpostor = session.getJugadores().get(impId);

                // Si por alguna razón el nombre es null, ponemos un fallback
                if (nombreImpostor == null) {
                    if (impId.equals(session.getAdminId())) {
                        nombreImpostor = "El Admin (Tú)";
                    } else {
                        nombreImpostor = "Impostor Desconocido";
                    }
                }
                // -------------------------------

                return "VICTORIA_IMPOSTOR|" + apodoEliminado + "|" + nombreImpostor;
            }

            return "CONTINUAR|" + apodoEliminado;
        }
    }
}