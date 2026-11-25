package com.tienda.ms_pagos.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ms-pagos es DUENO de dos topicos con parametros distintos segun su flujo:
 *  - pedidos360.pagos : 3 particiones (clave usuarioId), 30 dias, min.insync=2 (dato financiero: no perderlo)
 *  - pedidos360.fraude: 1 particion (orden global de alertas), 90 dias, min.insync=2
 */
@Configuration
public class KafkaTopicosConfig {

    @Value("${app.kafka.replicas}")
    private int replicas;

    @Bean
    public NewTopic topicoPagos(@Value("${app.kafka.topicos.pagos.nombre}") String nombre,
                                @Value("${app.kafka.topicos.pagos.particiones}") int particiones,
                                @Value("${app.kafka.topicos.pagos.retencion-ms}") long retencion,
                                @Value("${app.kafka.topicos.pagos.min-isr}") int minIsr) {
        return Topicos.crear(nombre, particiones, replicas, retencion, "delete", minIsr);
    }

    @Bean
    public NewTopic topicoFraude(@Value("${app.kafka.topicos.fraude.nombre}") String nombre,
                                 @Value("${app.kafka.topicos.fraude.particiones}") int particiones,
                                 @Value("${app.kafka.topicos.fraude.retencion-ms}") long retencion,
                                 @Value("${app.kafka.topicos.fraude.min-isr}") int minIsr) {
        return Topicos.crear(nombre, particiones, replicas, retencion, "delete", minIsr);
    }
}
