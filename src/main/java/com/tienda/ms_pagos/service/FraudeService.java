package com.tienda.ms_pagos.service;

import com.tienda.ms_pagos.dto.AlertaFraudeEvento;
import com.tienda.ms_pagos.messaging.PagoPublisher;
import com.tienda.ms_pagos.model.AlertaFraude;
import com.tienda.ms_pagos.model.Pago;
import com.tienda.ms_pagos.repository.AlertaFraudeRepository;
import com.tienda.ms_pagos.repository.PagoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Regla antifraude (vista en clases): si la MISMA tarjeta se usa MAS DE 3 VECES en 10 SEGUNDOS,
 * el intento se bloquea preventivamente y se registra una alerta (BD + Kafka "pedidos360.fraude").
 * Cuentan todos los intentos (aprobados, rechazados y bloqueados): asi se frena tambien el "card testing".
 */
@Service
public class FraudeService {

    private static final Logger log = LoggerFactory.getLogger(FraudeService.class);
    public static final String REGLA = "MISMA_TARJETA_MAS_DE_N_VECES_EN_VENTANA";

    private final PagoRepository pagos;
    private final AlertaFraudeRepository alertas;
    private final PagoPublisher publisher;
    private final int maxIntentos;
    private final int ventanaSegundos;
    private final Clock reloj;

    @Autowired
    public FraudeService(PagoRepository pagos, AlertaFraudeRepository alertas, PagoPublisher publisher,
                         @Value("${app.fraude.max-intentos:3}") int maxIntentos,
                         @Value("${app.fraude.ventana-segundos:10}") int ventanaSegundos) {
        this(pagos, alertas, publisher, maxIntentos, ventanaSegundos, Clock.systemUTC());
    }

    FraudeService(PagoRepository pagos, AlertaFraudeRepository alertas, PagoPublisher publisher,
                  int maxIntentos, int ventanaSegundos, Clock reloj) {
        this.pagos = pagos;
        this.alertas = alertas;
        this.publisher = publisher;
        this.maxIntentos = maxIntentos;
        this.ventanaSegundos = ventanaSegundos;
        this.reloj = reloj;
    }

    /** Intentos PREVIOS con esta huella dentro de la ventana (el intento actual aun no esta guardado). */
    public long intentosRecientes(String huella) {
        return pagos.countByTarjetaHuellaAndIntentoAfter(huella, Instant.now(reloj).minusSeconds(ventanaSegundos));
    }

    /** true si este intento seria el numero (maxIntentos + 1) o mas dentro de la ventana. */
    public boolean esSospechoso(long intentosPrevios) {
        return intentosPrevios >= maxIntentos;
    }

    /** Registra la alerta y la publica en Kafka. El pago ya debe estar guardado (con id). */
    public AlertaFraude registrarAlerta(Pago pago, long intentosPrevios) {
        AlertaFraude alerta = alertas.save(new AlertaFraude(pago.getUsuarioId(), pago.getId(), pago.getTarjetaMascara(),
                pago.getTarjetaHuella(), (int) intentosPrevios + 1, ventanaSegundos, pago.getMonto(), REGLA, Instant.now(reloj)));
        log.warn("[FRAUDE] Pago {} BLOQUEADO: tarjeta {} usada {} veces en {} s (usuario {})", pago.getId(),
                pago.getTarjetaMascara(), alerta.getIntentos(), ventanaSegundos, pago.getUsuarioId());
        publisher.alertaFraude(new AlertaFraudeEvento(UUID.randomUUID().toString(), "ALERTA_FRAUDE", alerta.getId(),
                pago.getId(), pago.getUsuarioId(), pago.getTarjetaMascara(), alerta.getIntentos(), ventanaSegundos,
                pago.getMonto(), REGLA, alerta.getFecha().toString()), pago.getTarjetaHuella());
        return alerta;
    }

    public int getMaxIntentos() { return maxIntentos; }
    public int getVentanaSegundos() { return ventanaSegundos; }
}
