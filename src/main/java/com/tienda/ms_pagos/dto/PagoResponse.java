package com.tienda.ms_pagos.dto;

import com.tienda.ms_pagos.model.Pago;

public record PagoResponse(Long id, String estado, String metodo, double monto, String tarjeta,
                           String codigoAutorizacion, String detalle, String creado) {

    public static PagoResponse desde(Pago p) {
        return new PagoResponse(p.getId(), p.getEstado().name(), p.getMetodo().name(),
                p.getMonto() == null ? 0 : p.getMonto(), p.getTarjetaMascara(), p.getCodigoAutorizacion(),
                p.getDetalle(), p.getCreado() == null ? null : p.getCreado().toString());
    }
}
