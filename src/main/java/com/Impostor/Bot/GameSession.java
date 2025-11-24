package com.Impostor.Bot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class GameSession {
    private Long adminId;
    private String adminName;

    // Key: UserID, Value: Apodo
    private Map<Long, String> jugadores = new HashMap<>();

    // IDs de los jugadores que siguen vivos
    private Set<Long> jugadoresVivos = new HashSet<>();

    private boolean enJuego = false;
    private Long impostorId;
    private String palabraSecreta;
    private String categoriaActual;
    // ... (tus variables existentes)

    // Mapa de Votos: ID_Votante -> ID_Sospechoso
    private Map<Long, Long> votosActuales = new HashMap<>();

    // Métodos para manejar votos
    public void limpiarVotos() {
        votosActuales.clear();
    }

    public void registrarVoto(Long votanteId, Long sospechosoId) {
        votosActuales.put(votanteId, sospechosoId);
    }

    public boolean yaVoto(Long votanteId) {
        return votosActuales.containsKey(votanteId);
    }

    public int getCantidadVotos() {
        return votosActuales.size();
    }

    public Map<Long, Long> getVotosActuales() {
        return votosActuales;
    }

    public GameSession(Long adminId, String adminName) {
        this.adminId = adminId;
        this.adminName = adminName;
        // El creador se agrega automáticamente
        this.jugadores.put(adminId, adminName);
    }

    public void iniciarRonda(Long impostorId, String palabra, String categoria) {
        this.impostorId = impostorId;
        this.palabraSecreta = palabra;
        this.categoriaActual = categoria;
        this.enJuego = true;
        // Al inicio, todos los registrados están vivos
        this.jugadoresVivos.addAll(jugadores.keySet());
    }

    public boolean esImpostor(Long id) {
        return id.equals(impostorId);
    }
    public void reiniciarSesion() {
        this.enJuego = false;
        this.impostorId = null;
        this.palabraSecreta = null;
        this.categoriaActual = null;
        this.votosActuales.clear();

        // Revivimos a todos los jugadores actuales
        this.jugadoresVivos.clear();
        this.jugadoresVivos.addAll(this.jugadores.keySet());
    }

    public Long getAdminId() { return adminId; }
    public Map<Long, String> getJugadores() { return jugadores; }
    public boolean isEnJuego() { return enJuego; }
    public Set<Long> getJugadoresVivos() { return jugadoresVivos; }
    public void eliminarJugador(Long id) { jugadoresVivos.remove(id); }
    public String getPalabraSecreta() { return palabraSecreta; }

    // Buscar ID por Apodo (ignorando mayúsculas)
    public Long buscarIdPorApodo(String apodoBuscado) {
        for (Map.Entry<Long, String> entry : jugadores.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(apodoBuscado)) {
                return entry.getKey();
            }
        }
        return null;
    }
}