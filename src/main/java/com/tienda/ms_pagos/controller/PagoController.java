package com.tienda.ms_pagos.controller;

import com.tienda.ms_pagos.dto.PagoResponse;
import com.tienda.ms_pagos.dto.PagoTarjetaRequest;
import com.tienda.ms_pagos.model.AlertaFraude;
import com.tienda.ms_pagos.model.Pago;
import com.tienda.ms_pagos.repository.AlertaFraudeRepository;
import com.tienda.ms_pagos.security.Seguridad;
import com.tienda.ms_pagos.security.UsuarioActual;
import com.tienda.ms_pagos.service.FraudeService;
import com.tienda.ms_pagos.service.PagoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 *   POST /api/pagos/tarjeta          -> pago con tarjeta simulada: 201 APROBADO | 402 RECHAZADO | 423 BLOQUEADO_FRAUDE
 *   GET  /api/pagos                  -> mis pagos
 *   GET  /api/pagos/{id}             -> detalle (propio, o cualquiera si es ADMIN)
 *   GET  /api/pagos/todos            -> ultimos 100 pagos (ADMIN)
 *   GET  /api/pagos/fraude/alertas   -> alertas antifraude (ADMIN)
 *   GET  /api/pagos/fraude/regla     -> parametros de la regla antifraude
 */
@RestController
@RequestMapping("/api/pagos")
public class PagoController {

    private final PagoService service;
    private final FraudeService fraude;
    private final AlertaFraudeRepository alertas;

    public PagoController(PagoService service, FraudeService fraude, AlertaFraudeRepository alertas) {
        this.service = service;
        this.fraude = fraude;
        this.alertas = alertas;
    }

    @PostMapping("/tarjeta")
    @PreAuthorize(Seguridad.CLIENTE)
    public ResponseEntity<PagoResponse> pagarConTarjeta(@AuthenticationPrincipal Jwt jwt, @RequestBody PagoTarjetaRequest req) {
        Pago pago = service.pagarConTarjeta(UsuarioActual.id(jwt), UsuarioActual.email(jwt), UsuarioActual.nombre(jwt), req);
        HttpStatus status = switch (pago.getEstado()) {
            case APROBADO -> HttpStatus.CREATED;
            case BLOQUEADO_FRAUDE -> HttpStatus.LOCKED;
            default -> HttpStatus.PAYMENT_REQUIRED;
        };
        return ResponseEntity.status(status).body(PagoResponse.desde(pago));
    }

    @GetMapping
    @PreAuthorize(Seguridad.CLIENTE)
    public List<PagoResponse> misPagos(@AuthenticationPrincipal Jwt jwt) {
        return service.misPagos(UsuarioActual.id(jwt)).stream().map(PagoResponse::desde).toList();
    }

    @GetMapping("/todos")
    @PreAuthorize(Seguridad.ADMIN)
    public List<PagoResponse> todos() {
        return service.todos().stream().map(PagoResponse::desde).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize(Seguridad.CLIENTE)
    public ResponseEntity<PagoResponse> porId(@AuthenticationPrincipal Jwt jwt, Authentication auth, @PathVariable Long id) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return service.buscar(id)
                .filter(p -> admin || p.getUsuarioId().equals(UsuarioActual.id(jwt)))
                .map(p -> ResponseEntity.ok(PagoResponse.desde(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/fraude/alertas")
    @PreAuthorize(Seguridad.ADMIN)
    public List<AlertaFraude> alertas() {
        return alertas.findTop100ByOrderByFechaDesc();
    }

    @GetMapping("/fraude/regla")
    @PreAuthorize(Seguridad.CLIENTE)
    public Map<String, Object> regla() {
        return Map.of("regla", FraudeService.REGLA, "maxIntentos", fraude.getMaxIntentos(),
                "ventanaSegundos", fraude.getVentanaSegundos(),
                "descripcion", "Mas de " + fraude.getMaxIntentos() + " usos de la misma tarjeta en "
                        + fraude.getVentanaSegundos() + " s -> bloqueo preventivo + alerta");
    }
}
