package com.tienda.ms_pagos.model;

import jakarta.persistence.*;

import java.time.Instant;

/** Alerta registrada cuando la regla antifraude bloquea un pago. */
@Entity
@Table(name = "alertas_fraude")
public class AlertaFraude {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id")
    private String usuarioId;
    @Column(name = "pago_id")
    private Long pagoId;
    @Column(name = "tarjeta_mascara")
    private String tarjetaMascara;
    @Column(name = "tarjeta_huella")
    private String tarjetaHuella;
    /** Intentos con la misma tarjeta dentro de la ventana, incluyendo el bloqueado. */
    private Integer intentos;
    @Column(name = "ventana_segundos")
    private Integer ventanaSegundos;
    private Double monto;
    private String regla;
    private Instant fecha;

    public AlertaFraude() {}

    public AlertaFraude(String usuarioId, Long pagoId, String tarjetaMascara, String tarjetaHuella, int intentos,
                        int ventanaSegundos, Double monto, String regla, Instant fecha) {
        this.usuarioId = usuarioId;
        this.pagoId = pagoId;
        this.tarjetaMascara = tarjetaMascara;
        this.tarjetaHuella = tarjetaHuella;
        this.intentos = intentos;
        this.ventanaSegundos = ventanaSegundos;
        this.monto = monto;
        this.regla = regla;
        this.fecha = fecha;
    }

    public Long getId() { return id; }
    public String getUsuarioId() { return usuarioId; }
    public Long getPagoId() { return pagoId; }
    public String getTarjetaMascara() { return tarjetaMascara; }
    public Integer getIntentos() { return intentos; }
    public Integer getVentanaSegundos() { return ventanaSegundos; }
    public Double getMonto() { return monto; }
    public String getRegla() { return regla; }
    public Instant getFecha() { return fecha; }
}
