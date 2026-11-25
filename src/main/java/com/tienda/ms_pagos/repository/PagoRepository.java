package com.tienda.ms_pagos.repository;

import com.tienda.ms_pagos.model.Pago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    /** Intentos con la MISMA tarjeta desde un instante (ventana antifraude). */
    long countByTarjetaHuellaAndIntentoAfter(String tarjetaHuella, Instant desde);

    Optional<Pago> findByToken(String token);

    Optional<Pago> findByBuyOrder(String buyOrder);

    List<Pago> findTop50ByUsuarioIdOrderByCreadoDesc(String usuarioId);

    List<Pago> findTop100ByOrderByCreadoDesc();
}
