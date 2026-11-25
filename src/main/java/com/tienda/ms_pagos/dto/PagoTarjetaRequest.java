package com.tienda.ms_pagos.dto;

/** Pago con tarjeta SIMULADA. vencimiento = "MM/AA". */
public record PagoTarjetaRequest(String numero, String vencimiento, String cvv, String titular, Double monto) {}
