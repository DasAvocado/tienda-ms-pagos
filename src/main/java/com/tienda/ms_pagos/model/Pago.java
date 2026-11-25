package com.tienda.ms_pagos.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Cada INTENTO de pago queda registrado (aprobado, rechazado o bloqueado): es la base de la regla antifraude.
 * La tarjeta nunca se guarda completa: solo su huella SHA-256 (para comparar) y la mascara (**** 6623).
 */
@Entity
@Table(name = "pagos", indexes = @Index(name = "idx_pagos_huella_creado", columnList = "tarjeta_huella,intento"))
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private String usuarioId;
    private String email;
    private String nombre;
    private Double monto;

    @Enumerated(EnumType.STRING)
    private MetodoPago metodo;

    @Enumerated(EnumType.STRING)
    private EstadoPago estado;

    @Column(name = "tarjeta_huella")
    private String tarjetaHuella;
    @Column(name = "tarjeta_mascara")
    private String tarjetaMascara;

    // ---- Webpay Plus ----
    @Column(name = "buy_order")
    private String buyOrder;
    @Column(name = "session_id")
    private String sessionId;
    @Column(length = 100)
    private String token;
    @Column(name = "codigo_autorizacion")
    private String codigoAutorizacion;
    @Column(name = "front_origin")
    private String frontOrigin;

    @Column(length = 500)
    private String detalle;
    private Instant creado;
    private Instant actualizado;
    /** Momento en que se presento la tarjeta (simulada: al pagar; Webpay: al confirmar). Base de la ventana antifraude. */
    private Instant intento;

    public Pago() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public Double getMonto() { return monto; }
    public void setMonto(Double monto) { this.monto = monto; }
    public MetodoPago getMetodo() { return metodo; }
    public void setMetodo(MetodoPago metodo) { this.metodo = metodo; }
    public EstadoPago getEstado() { return estado; }
    public void setEstado(EstadoPago estado) { this.estado = estado; }
    public String getTarjetaHuella() { return tarjetaHuella; }
    public void setTarjetaHuella(String tarjetaHuella) { this.tarjetaHuella = tarjetaHuella; }
    public String getTarjetaMascara() { return tarjetaMascara; }
    public void setTarjetaMascara(String tarjetaMascara) { this.tarjetaMascara = tarjetaMascara; }
    public String getBuyOrder() { return buyOrder; }
    public void setBuyOrder(String buyOrder) { this.buyOrder = buyOrder; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getCodigoAutorizacion() { return codigoAutorizacion; }
    public void setCodigoAutorizacion(String codigoAutorizacion) { this.codigoAutorizacion = codigoAutorizacion; }
    public String getFrontOrigin() { return frontOrigin; }
    public void setFrontOrigin(String frontOrigin) { this.frontOrigin = frontOrigin; }
    public String getDetalle() { return detalle; }
    public void setDetalle(String detalle) { this.detalle = detalle; }
    public Instant getCreado() { return creado; }
    public void setCreado(Instant creado) { this.creado = creado; }
    public Instant getActualizado() { return actualizado; }
    public Instant getIntento() { return intento; }
    public void setIntento(Instant intento) { this.intento = intento; }
    public void setActualizado(Instant actualizado) { this.actualizado = actualizado; }
}
