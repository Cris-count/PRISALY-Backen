package com.prisaly.m7;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prisaly.m7.domain.ClaseErrorCorreo;
import com.prisaly.m7.domain.ResultadoEnvioSmtp;
import com.prisaly.m7.infrastructure.FabricaProveedorCorreo;
import com.prisaly.m7.infrastructure.HttpApiProveedorCorreoAdapter;
import com.prisaly.m7.infrastructure.JavaMailProveedorCorreoAdapter;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mock.env.MockEnvironment;

/** DC-DEPLOY-03: M7 delivery through a transactional e-mail HTTPS API (same port, same error classes as SMTP). */
class HttpApiProveedorCorreoAdapterTest {

    private static final String CLAVE = "re_prueba_no_real_0123456789";
    private HttpServer servidor;
    private final AtomicReference<String> cuerpo = new AtomicReference<>();
    private final AtomicReference<String> autorizacion = new AtomicReference<>();
    private final AtomicReference<String> tipo = new AtomicReference<>();

    private HttpApiProveedorCorreoAdapter adaptador(int estado, String respuesta) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/emails", intercambio -> {
            cuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            autorizacion.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            tipo.set(intercambio.getRequestHeaders().getFirst("Content-Type"));
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(estado, bytes.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(bytes);
            }
        });
        servidor.start();
        URI url = URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + "/emails");
        return new HttpApiProveedorCorreoAdapter(HttpClient.newHttpClient(), url, CLAVE, Duration.ofSeconds(5));
    }

    @AfterEach
    void parar() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void aceptadoDevuelveElIdDelProveedorYEnviaJsonEscapadoConBearer() throws IOException {
        var resultado = adaptador(200, "{\"id\":\"49a3999c-0ce1-4ea6-ab68-afcd6dc2e794\"}")
                .enviar("PRISALY <no-reply@prisaly.test>", "docente@prisaly.test", "Activa tu cuenta \"PRISALY\"",
                        "Línea 1\nLínea 2 \\ fin");
        assertThat(resultado).isEqualTo(new ResultadoEnvioSmtp.Aceptado("49a3999c-0ce1-4ea6-ab68-afcd6dc2e794"));
        assertThat(autorizacion.get()).isEqualTo("Bearer " + CLAVE);
        assertThat(tipo.get()).startsWith("application/json");
        assertThat(cuerpo.get()).isEqualTo("{\"from\":\"PRISALY <no-reply@prisaly.test>\",\"to\":[\"docente@prisaly.test\"],"
                + "\"subject\":\"Activa tu cuenta \\\"PRISALY\\\"\",\"text\":\"Línea 1\\nLínea 2 \\\\ fin\"}");
    }

    @Test
    void credencialRechazadaEsConfiguracionYNoExponeLaClave() throws IOException {
        var resultado = (ResultadoEnvioSmtp.Rechazado) adaptador(401, "{\"message\":\"API key is invalid\"}")
                .enviar("a@prisaly.test", "b@prisaly.test", "A", "C");
        assertThat(resultado.clase()).isEqualTo(ClaseErrorCorreo.CONFIGURACION);
        assertThat(resultado.codigo()).isEqualTo("535");
        assertThat(resultado.detalle()).doesNotContain(CLAVE);
    }

    @Test
    void remitenteNoAutorizadoEsConfiguracion() throws IOException {
        var resultado = (ResultadoEnvioSmtp.Rechazado) adaptador(403, "{\"message\":\"domain is not verified\"}")
                .enviar("a@prisaly.test", "b@prisaly.test", "A", "C");
        assertThat(resultado.clase()).isEqualTo(ClaseErrorCorreo.CONFIGURACION);
    }

    @Test
    void destinatarioInvalidoEsPermanente() throws IOException {
        var resultado = (ResultadoEnvioSmtp.Rechazado) adaptador(422, "{\"message\":\"Invalid `to` field\"}")
                .enviar("a@prisaly.test", "no-es-correo", "A", "C");
        assertThat(resultado.clase()).isEqualTo(ClaseErrorCorreo.PERMANENTE_DESTINATARIO);
        assertThat(resultado.codigo()).isEqualTo("550");
    }

    @Test
    void limiteDeTasaYErrorDelServidorSonTransitorios() throws IOException {
        assertThat(((ResultadoEnvioSmtp.Rechazado) adaptador(429, "{}").enviar("a@x.test", "b@x.test", "A", "C")).clase())
                .isEqualTo(ClaseErrorCorreo.TRANSITORIO);
        servidor.stop(0);
        assertThat(((ResultadoEnvioSmtp.Rechazado) adaptador(503, "").enviar("a@x.test", "b@x.test", "A", "C")).clase())
                .isEqualTo(ClaseErrorCorreo.TRANSITORIO);
    }

    @Test
    void sinConexionEsTransitorio() throws IOException {
        int puerto;
        try (ServerSocket libre = new ServerSocket(0)) {
            puerto = libre.getLocalPort();
        }
        var adaptador = new HttpApiProveedorCorreoAdapter(HttpClient.newHttpClient(),
                URI.create("http://127.0.0.1:" + puerto + "/emails"), CLAVE, Duration.ofSeconds(2));
        var resultado = (ResultadoEnvioSmtp.Rechazado) adaptador.enviar("a@x.test", "b@x.test", "A", "C");
        assertThat(resultado.clase()).isEqualTo(ClaseErrorCorreo.TRANSITORIO);
        assertThat(resultado.detalle()).doesNotContain(CLAVE);
    }

    @Test
    void exigeHttpsFueraDeLaMaquinaLocalYClave() {
        assertThatThrownBy(() -> new HttpApiProveedorCorreoAdapter(HttpClient.newHttpClient(),
                URI.create("http://api.proveedor.test/emails"), CLAVE, Duration.ofSeconds(2)))
                .hasMessageContaining("M7-CORREO-HTTP-URL");
        assertThatThrownBy(() -> new HttpApiProveedorCorreoAdapter(HttpClient.newHttpClient(),
                URI.create("https://api.resend.com/emails"), " ", Duration.ofSeconds(2)))
                .hasMessageContaining("M7-CORREO-HTTP-CLAVE");
    }

    // --- FabricaProveedorCorreo ---

    private static org.springframework.beans.factory.ObjectProvider<JavaMailSender> mail(JavaMailSender sender) {
        DefaultListableBeanFactory fabrica = new DefaultListableBeanFactory();
        if (sender != null) {
            fabrica.registerSingleton("mailSender", sender);
        }
        return fabrica.getBeanProvider(JavaMailSender.class);
    }

    @Test
    void fabricaPorDefectoUsaSmtpComoAntes() {
        var proveedor = FabricaProveedorCorreo.crear(new MockEnvironment(), mail(new JavaMailSenderImpl()));
        assertThat(proveedor).isInstanceOf(JavaMailProveedorCorreoAdapter.class);
    }

    @Test
    void fabricaSmtpSinServidorDeCorreoFallaAlArrancar() {
        assertThatThrownBy(() -> FabricaProveedorCorreo.crear(new MockEnvironment(), mail(null)))
                .hasMessageContaining("M7-CORREO-SMTP");
    }

    @Test
    void fabricaHttpNoNecesitaSmtpPeroExigeLaClave() {
        var env = new MockEnvironment().withProperty("prisaly.m7.correo.transporte", "http");
        assertThatThrownBy(() -> FabricaProveedorCorreo.crear(env, mail(null))).hasMessageContaining("M7-CORREO-HTTP-CLAVE");
        env.setProperty("prisaly.m7.correo.http.clave", CLAVE);
        assertThat(FabricaProveedorCorreo.crear(env, mail(null))).isInstanceOf(HttpApiProveedorCorreoAdapter.class);
    }

    @Test
    void fabricaRechazaTransporteDesconocidoYTimeoutFueraDeRango() {
        assertThatThrownBy(() -> FabricaProveedorCorreo.crear(
                new MockEnvironment().withProperty("prisaly.m7.correo.transporte", "pigeon"), mail(null)))
                .hasMessageContaining("M7-CORREO-TRANSPORTE");
        var env = new MockEnvironment().withProperty("prisaly.m7.correo.transporte", "http")
                .withProperty("prisaly.m7.correo.http.clave", CLAVE).withProperty("prisaly.m7.correo.http.timeout-ms", "100");
        assertThatThrownBy(() -> FabricaProveedorCorreo.crear(env, mail(null))).hasMessageContaining("M7-CORREO-HTTP-TIMEOUT");
    }
}
