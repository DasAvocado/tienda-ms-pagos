package com.tienda.ms_pagos.controller;

import com.tienda.ms_pagos.dto.WebpayIniciarRequest;
import com.tienda.ms_pagos.dto.WebpayIniciarResponse;
import com.tienda.ms_pagos.security.Seguridad;
import com.tienda.ms_pagos.security.UsuarioActual;
import com.tienda.ms_pagos.service.PagoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * Webpay Plus (Transbank, ambiente de integracion):
 *   POST     /api/pagos/webpay/iniciar  {monto, returnOrigin} -> {pagoId, token, url}   (JWT, rol CLIENTE)
 *   GET|POST /api/pagos/webpay/retorno  <- Webpay devuelve aqui al navegador (PUBLICO: Transbank no envia JWT)
 *            confirma la transaccion y redirige (302) a {returnOrigin}/pago/resultado?pagoId=..&estado=..
 */
@RestController
@RequestMapping("/api/pagos/webpay")
public class WebpayController {

    private final PagoService service;

    public WebpayController(PagoService service) {
        this.service = service;
    }

    @PostMapping("/iniciar")
    @PreAuthorize(Seguridad.CLIENTE)
    public ResponseEntity<WebpayIniciarResponse> iniciar(@AuthenticationPrincipal Jwt jwt, @RequestBody WebpayIniciarRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.iniciarWebpay(UsuarioActual.id(jwt),
                UsuarioActual.email(jwt), UsuarioActual.nombre(jwt), req));
    }

    @RequestMapping(value = "/retorno", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Void> retorno(@RequestParam(name = "token_ws", required = false) String tokenWs,
                                        @RequestParam(name = "TBK_TOKEN", required = false) String tbkToken,
                                        @RequestParam(name = "TBK_ORDEN_COMPRA", required = false) String tbkOrden) {
        String destino = service.retornoWebpay(tokenWs, tbkToken, tbkOrden)
                .map(service::urlResultado)
                .orElseGet(service::urlResultadoDesconocido);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(destino)).build();
    }
}
