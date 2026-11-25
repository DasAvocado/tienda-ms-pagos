package com.tienda.ms_pagos.dto;

/** DTO del topico Kafka "pedidos360.fraude". */
public record AlertaFraudeEvento(
        String eventoId,
        String tipo,
        Long alertaId,
        Long pagoId,
        String usuarioId,
        String tarjeta,
        Integer intentos,
        Integer ventanaSegundos,
        Double monto,
        String regla,
        String fecha) {}
