package com.tienda.ms_pagos.dto;

/**
 * DTO del topico Kafka "pedidos360.pagos".
 * tipo: PAGO_APROBADO | PAGO_RECHAZADO | PAGO_BLOQUEADO_FRAUDE | PAGO_ANULADO
 */
public record EventoPago(
        String eventoId,
        String tipo,
        Long pagoId,
        String usuarioId,
        Double monto,
        String metodo,
        String tarjeta,
        String detalle,
        String fecha) {}
