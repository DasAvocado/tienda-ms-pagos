package com.tienda.ms_pagos.service;

import com.tienda.ms_pagos.dto.PagoTarjetaRequest;
import com.tienda.ms_pagos.dto.WebpayIniciarRequest;
import com.tienda.ms_pagos.dto.WebpayIniciarResponse;
import com.tienda.ms_pagos.messaging.PagoPublisher;
import com.tienda.ms_pagos.model.EstadoPago;
import com.tienda.ms_pagos.model.MetodoPago;
import com.tienda.ms_pagos.model.Pago;
import com.tienda.ms_pagos.repository.PagoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Logica de pagos (sin detalles de RabbitMQ/Kafka: usa PagoPublisher).
 * Los metodos NO son @Transactional a proposito: cada intento se guarda (commit) dentro del bloqueo por tarjeta,
 * asi el siguiente intento con la misma tarjeta ya lo ve al contar la ventana antifraude.
 */
@Service
public class PagoService {

    private static final Logger log = LoggerFactory.getLogger(PagoService.class);
    private static final double MONTO_MAXIMO = 20_000_000;

    private final PagoRepository pagos;
    private final FraudeService fraude;
    private final PagoPublisher publisher;
    private final WebpayClient webpay;
    private final String returnUrl;
    private final List<String> origenesPermitidos;
    private final Map<String, Object> bloqueos = new ConcurrentHashMap<>();

    public PagoService(PagoRepository pagos, FraudeService fraude, PagoPublisher publisher, WebpayClient webpay,
                       @Value("${app.webpay.return-url}") String returnUrl,
                       @Value("${app.webpay.front-origins:*}") List<String> origenesPermitidos) {
        this.pagos = pagos;
        this.fraude = fraude;
        this.publisher = publisher;
        this.webpay = webpay;
        this.returnUrl = returnUrl;
        this.origenesPermitidos = origenesPermitidos;
    }

    // ------------------------------------------------------------------ tarjeta simulada

    public Pago pagarConTarjeta(String usuarioId, String email, String nombre, PagoTarjetaRequest req) {
        validarMonto(req.monto());
        String digitos = ValidadorTarjeta.soloDigitos(req.numero());
        if (!ValidadorTarjeta.luhn(digitos)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Numero de tarjeta invalido");
        if (!ValidadorTarjeta.vencimientoValido(req.vencimiento(), YearMonth.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vencimiento invalido o tarjeta vencida (MM/AA)");
        if (!ValidadorTarjeta.cvvValido(req.cvv())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CVV invalido");

        String huella = ValidadorTarjeta.huella(digitos);
        Pago pago = nuevo(usuarioId, email, nombre, req.monto(), MetodoPago.TARJETA_SIMULADA);
        pago.setTarjetaHuella(huella);
        pago.setTarjetaMascara(ValidadorTarjeta.mascara(digitos));

        synchronized (bloqueo(huella)) {
            long previos = fraude.intentosRecientes(huella);
            pago.setIntento(Instant.now());
            if (fraude.esSospechoso(previos)) {
                bloquear(pago, previos);
            } else if (ValidadorTarjeta.TARJETA_RECHAZADA.equals(digitos)) {
                pago.setEstado(EstadoPago.RECHAZADO);
                pago.setDetalle("Rechazada por el emisor (tarjeta de prueba 4000 0000 0000 0002)");
                pago = pagos.save(pago);
            } else {
                pago.setEstado(EstadoPago.APROBADO);
                pago.setCodigoAutorizacion(String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000)));
                pago.setDetalle("Pago simulado aprobado");
                pago = pagos.save(pago);
            }
        }
        publisher.resultado(pago);
        log.info("Pago {} con tarjeta {} -> {}", pago.getId(), pago.getTarjetaMascara(), pago.getEstado());
        return pago;
    }

    // ------------------------------------------------------------------ Webpay Plus

    public WebpayIniciarResponse iniciarWebpay(String usuarioId, String email, String nombre, WebpayIniciarRequest req) {
        validarMonto(req.monto());
        Pago pago = nuevo(usuarioId, email, nombre, req.monto(), MetodoPago.WEBPAY);
        pago.setEstado(EstadoPago.PENDIENTE);
        pago.setFrontOrigin(origenValido(req.returnOrigin()));
        pago = pagos.save(pago);

        String buyOrder = "P360-" + pago.getId() + "-" + (System.currentTimeMillis() % 100000);   // max 26 caracteres
        String sessionId = usuarioId.length() > 61 ? usuarioId.substring(0, 61) : usuarioId;
        try {
            Map<String, Object> r = webpay.crear(buyOrder, sessionId, Math.round(req.monto()), returnUrl);
            pago.setBuyOrder(buyOrder);
            pago.setSessionId(sessionId);
            pago.setToken(String.valueOf(r.get("token")));
            pago.setActualizado(Instant.now());
            pagos.save(pago);
            return new WebpayIniciarResponse(pago.getId(), pago.getToken(), String.valueOf(r.get("url")));
        } catch (RuntimeException e) {
            log.error("[Webpay] No se pudo crear la transaccion: {}", e.getMessage());
            pago.setEstado(EstadoPago.RECHAZADO);
            pago.setDetalle("Webpay no disponible: " + recortar(e.getMessage()));
            pagos.save(pago);
            publisher.resultado(pago);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo iniciar el pago en Webpay");
        }
    }

    /**
     * Retorno desde Webpay (el navegador del cliente vuelve aqui):
     *  - token_ws            -> se confirma (commit) la transaccion
     *  - TBK_TOKEN           -> el usuario anulo el pago en el formulario de Webpay
     *  - solo TBK_ORDEN_COMPRA -> se agoto el tiempo
     */
    public Optional<Pago> retornoWebpay(String tokenWs, String tbkToken, String tbkOrdenCompra) {
        if (tokenWs != null && !tokenWs.isBlank() && (tbkToken == null || tbkToken.isBlank())) {
            return pagos.findByToken(tokenWs).map(this::confirmarWebpay);
        }
        Optional<Pago> pago = tbkToken != null && !tbkToken.isBlank() ? pagos.findByToken(tbkToken)
                : tbkOrdenCompra != null ? pagos.findByBuyOrder(tbkOrdenCompra) : Optional.empty();
        return pago.map(p -> {
            if (p.getEstado() != EstadoPago.PENDIENTE) return p;
            p.setEstado(EstadoPago.ANULADO);
            p.setDetalle(tbkToken != null ? "Pago anulado por el usuario en Webpay" : "Tiempo de pago agotado en Webpay");
            p.setActualizado(Instant.now());
            Pago guardado = pagos.save(p);
            publisher.resultado(guardado);
            return guardado;
        });
    }

    private Pago confirmarWebpay(Pago pago) {
        if (pago.getEstado() != EstadoPago.PENDIENTE) return pago;   // recarga de pagina: ya procesado
        Map<String, Object> r;
        try {
            r = webpay.confirmar(pago.getToken());
        } catch (RuntimeException e) {
            pago.setEstado(EstadoPago.RECHAZADO);
            pago.setDetalle("Error al confirmar en Webpay: " + recortar(e.getMessage()));
            pago.setActualizado(Instant.now());
            Pago guardado = pagos.save(pago);
            publisher.resultado(guardado);
            return guardado;
        }
        String ultimos4 = ultimos4(r.get("card_detail"));
        String huella = ValidadorTarjeta.huella("webpay:" + ultimos4);
        pago.setTarjetaHuella(huella);
        pago.setTarjetaMascara("**** " + ultimos4);
        boolean autorizado = "AUTHORIZED".equals(r.get("status")) && Integer.valueOf(0).equals(numero(r.get("response_code")));

        synchronized (bloqueo(huella)) {
            long previos = fraude.intentosRecientes(huella);
            pago.setIntento(Instant.now());
            pago.setActualizado(Instant.now());
            if (autorizado && fraude.esSospechoso(previos)) {
                try {
                    webpay.anular(pago.getToken(), Math.round(pago.getMonto()));
                } catch (RuntimeException e) {
                    log.error("[Webpay] No se pudo reembolsar el pago {} bloqueado por fraude: {}", pago.getId(), e.getMessage());
                }
                bloquear(pago, previos);
                pago.setDetalle(pago.getDetalle() + " (transaccion Webpay reembolsada)");
                pago = pagos.save(pago);
            } else if (autorizado) {
                pago.setEstado(EstadoPago.APROBADO);
                pago.setCodigoAutorizacion(String.valueOf(r.get("authorization_code")));
                pago.setDetalle("Webpay Plus autorizado (" + r.get("payment_type_code") + ")");
                pago = pagos.save(pago);
            } else {
                pago.setEstado(EstadoPago.RECHAZADO);
                pago.setDetalle("Webpay rechazo el pago (status=" + r.get("status") + ", response_code=" + r.get("response_code") + ")");
                pago = pagos.save(pago);
            }
        }
        publisher.resultado(pago);
        return pago;
    }

    // ------------------------------------------------------------------ consultas

    public List<Pago> misPagos(String usuarioId) {
        return pagos.findTop50ByUsuarioIdOrderByCreadoDesc(usuarioId);
    }

    public List<Pago> todos() {
        return pagos.findTop100ByOrderByCreadoDesc();
    }

    public Optional<Pago> buscar(Long id) {
        return pagos.findById(id);
    }

    /** URL del frontend a la que se redirige al cliente despues de Webpay. */
    public String urlResultado(Pago pago) {
        String origen = pago.getFrontOrigin() != null ? pago.getFrontOrigin() : origenValido(null);
        return origen + "/pago/resultado?pagoId=" + pago.getId() + "&estado=" + pago.getEstado().name();
    }

    public String urlResultadoDesconocido() {
        return origenValido(null) + "/pago/resultado?estado=DESCONOCIDO";
    }

    // ------------------------------------------------------------------ apoyo

    private void bloquear(Pago pago, long previos) {
        pago.setEstado(EstadoPago.BLOQUEADO_FRAUDE);
        pago.setDetalle("Bloqueado preventivamente: tarjeta usada " + (previos + 1) + " veces en "
                + fraude.getVentanaSegundos() + " s (maximo " + fraude.getMaxIntentos() + ")");
        Pago guardado = pagos.save(pago);
        pago.setId(guardado.getId());
        fraude.registrarAlerta(guardado, previos);
    }

    private Pago nuevo(String usuarioId, String email, String nombre, Double monto, MetodoPago metodo) {
        Pago p = new Pago();
        p.setUsuarioId(usuarioId);
        p.setEmail(email);
        p.setNombre(nombre);
        p.setMonto((double) Math.round(monto));
        p.setMetodo(metodo);
        p.setCreado(Instant.now());
        p.setActualizado(p.getCreado());
        return p;
    }

    private static void validarMonto(Double monto) {
        if (monto == null || monto < 1 || monto > MONTO_MAXIMO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Monto invalido");
        }
    }

    /** Evita redirecciones abiertas: solo origenes http(s) sin ruta y, si se configuro una lista, que esten en ella. */
    String origenValido(String origen) {
        String porDefecto = origenesPermitidos.stream().filter(o -> !"*".equals(o)).findFirst().orElse("http://localhost:4200");
        if (origen == null || origen.isBlank()) return porDefecto;
        try {
            URI u = URI.create(origen.trim());
            boolean forma = ("http".equals(u.getScheme()) || "https".equals(u.getScheme())) && u.getHost() != null
                    && (u.getPath() == null || u.getPath().isEmpty() || "/".equals(u.getPath())) && u.getQuery() == null;
            String limpio = u.getScheme() + "://" + u.getRawAuthority();
            if (forma && (origenesPermitidos.contains("*") || origenesPermitidos.contains(limpio))) return limpio;
        } catch (IllegalArgumentException ignorado) {
            // origen mal formado
        }
        log.warn("returnOrigin no permitido: {} -> se usa {}", origen, porDefecto);
        return porDefecto;
    }

    private Object bloqueo(String huella) {
        return bloqueos.computeIfAbsent(huella, k -> new Object());
    }

    private static String ultimos4(Object cardDetail) {
        if (cardDetail instanceof Map<?, ?> m && m.get("card_number") != null) {
            String n = String.valueOf(m.get("card_number")).replaceAll("\\D", "");
            return n.length() >= 4 ? n.substring(n.length() - 4) : n;
        }
        return "0000";
    }

    private static Integer numero(Object valor) {
        return valor instanceof Number n ? n.intValue() : null;
    }

    private static String recortar(String texto) {
        if (texto == null) return "";
        return texto.length() > 300 ? texto.substring(0, 300) : texto;
    }
}
