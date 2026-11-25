package com.tienda.ms_pagos.dto;

/** Comando RabbitMQ "pago.aprobado" -> ms-carrito cierra la compra (publica carrito.checkout). */
public record PagoAprobadoEvent(Long pagoId, String usuarioId, String email, String nombre, double monto,
                                String metodo, String fecha) {}
