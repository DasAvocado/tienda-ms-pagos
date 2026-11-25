package com.tienda.ms_pagos.messaging;

import com.tienda.ms_pagos.dto.AlertaFraudeEvento;
import com.tienda.ms_pagos.dto.EventoPago;
import com.tienda.ms_pagos.dto.PagoAprobadoEvent;
import com.tienda.ms_pagos.kafka.EventoKafkaPublisher;
import com.tienda.ms_pagos.model.EstadoPago;
import com.tienda.ms_pagos.model.Pago;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Productor del dominio PAGOS. Separa COMANDOS de HECHOS:
 *  - RabbitMQ "pago.aprobado"  -> COMANDO para ms-carrito (cerrar la compra una sola vez, con ACK y DLQ).
 *  - Kafka "pedidos360.pagos"  -> HECHO de cada intento (aprobado/rechazado/bloqueado) para metricas y auditoria.
 *  - Kafka "pedidos360.fraude" -> ALERTA antifraude (1 particion: orden global de alertas).
 */
@Component
public class PagoPublisher {

    private final RabbitTemplate rabbit;
    private final EventoKafkaPublisher kafka;
    private final String exchange;
    private final String rkPagoAprobado;
    private final String topicoPagos;
    private final String topicoFraude;

    public PagoPublisher(RabbitTemplate rabbit, EventoKafkaPublisher kafka,
                         @Value("${app.rabbit.exchange}") String exchange,
                         @Value("${app.rabbit.routing-keys.pago-aprobado}") String rkPagoAprobado,
                         @Value("${app.kafka.topicos.pagos.nombre}") String topicoPagos,
                         @Value("${app.kafka.topicos.fraude.nombre}") String topicoFraude) {
        this.rabbit = rabbit;
        this.kafka = kafka;
        this.exchange = exchange;
        this.rkPagoAprobado = rkPagoAprobado;
        this.topicoPagos = topicoPagos;
        this.topicoFraude = topicoFraude;
    }

    /** Publica el resultado del intento en Kafka y, si fue aprobado, el comando a ms-carrito. */
    public void resultado(Pago p) {
        kafka.publicar(topicoPagos, p.getUsuarioId(), new EventoPago(UUID.randomUUID().toString(),
                "PAGO_" + (p.getEstado() == EstadoPago.BLOQUEADO_FRAUDE ? "BLOQUEADO_FRAUDE" : p.getEstado().name()),
                p.getId(), p.getUsuarioId(), p.getMonto(), p.getMetodo().name(), p.getTarjetaMascara(), p.getDetalle(),
                Instant.now().toString()));
        if (p.getEstado() == EstadoPago.APROBADO) {
            rabbit.convertAndSend(exchange, rkPagoAprobado, new PagoAprobadoEvent(p.getId(), p.getUsuarioId(),
                    p.getEmail(), p.getNombre(), p.getMonto(), p.getMetodo().name(), Instant.now().toString()));
        }
    }

    public void alertaFraude(AlertaFraudeEvento alerta, String huellaTarjeta) {
        kafka.publicar(topicoFraude, huellaTarjeta.substring(0, 16), alerta);
    }
}
