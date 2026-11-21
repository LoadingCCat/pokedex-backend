package com.pokedex;

import com.pokedex.entity.Pokemon;
import com.pokedex.entity.Tipo;
import com.pokedex.repository.PokemonRepository;
import com.pokedex.repository.TipoRepository;
import com.pokedex.service.ResultadoVision;
import com.pokedex.service.ServicioVisionMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F20, criterios del back: POST /api/pokemons/identificar de punta a punta (HTTP → controller →
 * servicio → PostgreSQL embebido), con la visión simulada ({@link ServicioVisionMock}, perfil
 * {@code e2e}): ni gasta cuota ni sale a internet.
 *
 * <p>El límite por IP es un contador único de la aplicación, así que cada prueba usa su propia
 * IP (como lo haría cada cliente real) y el límite de una no afecta a las demás.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
class IdentificacionApiTest {

    private static final String RUTA = "/api/pokemons/identificar";
    private static final String MENSAJE_NO_RECONOCIDO = "No reconocimos un Pokémon en la foto. Prueba con otra más clara";
    private static final AtomicInteger SIGUIENTE_IP = new AtomicInteger();

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry propiedades) {
        PostgresEmbebido.registrar(propiedades);
        propiedades.add("pokedex.seguridad.jwt-secreto", () -> ConSeguridad.SECRETO_DE_PRUEBA);
        propiedades.add("pokedex.seguridad.admin-usuario", () -> "admin-prueba");
        propiedades.add("pokedex.seguridad.admin-contrasena", () -> "clave-de-prueba");
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private PokemonRepository pokemonRepository;
    @Autowired
    private TipoRepository tipoRepository;
    @Autowired
    private ServicioVisionMock vision;

    private RequestPostProcessor ip;
    private int llamadasAntes;

    /**
     * Charmander (fuego, 309, gen 1) y, por cercanía de total: Cyndaquil (309, gen 2),
     * Chimchar (309, gen 4), Torchic (310, gen 3). Más lejos: Squirtle (agua, 314) y Charizard (534).
     * Orden esperado de similares: 155, 390 (empatan en 0 y ninguno es de gen 1 → por idPokedex), 255.
     */
    @BeforeEach
    void datos() {
        pokemonRepository.deleteAll();
        tipoRepository.deleteAll();
        Tipo fuego = tipoRepository.save(new Tipo("fire"));
        Tipo agua = tipoRepository.save(new Tipo("water"));

        pokemon(4, "charmander", "Charmander", 1, fuego, 39, 52, 43, 60, 50, 65);
        pokemon(155, "cyndaquil", "Cyndaquil", 2, fuego, 39, 52, 43, 60, 50, 65);
        pokemon(390, "chimchar", "Chimchar", 4, fuego, 44, 58, 44, 58, 44, 61);
        pokemon(255, "torchic", "Torchic", 3, fuego, 45, 60, 40, 70, 50, 45);
        pokemon(7, "squirtle", "Squirtle", 1, agua, 44, 48, 65, 50, 64, 43);
        pokemon(6, "charizard", "Charizard", 1, fuego, 78, 84, 78, 109, 85, 100);

        vision.responder(new ResultadoVision(true, "charmander", 0.93));
        llamadasAntes = vision.llamadas();
        String direccion = "10.20.30." + SIGUIENTE_IP.incrementAndGet();
        ip = peticion -> {
            peticion.setRemoteAddr(direccion);
            return peticion;
        };
    }

    private void pokemon(int id, String nombre, String nombreEs, int generacion, Tipo tipo,
                         int ps, int ataque, int defensa, int ataqueEsp, int defensaEsp, int velocidad) {
        Pokemon p = new Pokemon();
        p.setIdPokedex(id);
        p.setNombre(nombre);
        p.setNombreEs(nombreEs);
        p.setGeneracion(generacion);
        p.setImagenUrl("https://img.test/" + id + ".png");
        p.setPs(ps);
        p.setAtaque(ataque);
        p.setDefensa(defensa);
        p.setAtaqueEspecial(ataqueEsp);
        p.setDefensaEspecial(defensaEsp);
        p.setVelocidad(velocidad);
        p.setTipos(List.of(tipo));
        pokemonRepository.save(p);
    }

    // ---------- fotos de prueba ----------

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile("foto", "foto.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3, 4});
    }

    private static MockMultipartFile conBytes(String nombre, String contentType, byte[] bytes) {
        return new MockMultipartFile("foto", nombre, contentType, bytes);
    }

    private int llamadasDeVision() {
        return vision.llamadas() - llamadasAntes;
    }

    // ---------- 200 ----------

    @Test
    void exitoDevuelveElIdentificadoYTresSimilaresDistintosConSuMotivo() throws Exception {
        mvc.perform(multipart(RUTA).file(jpeg()).with(ip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificado.idPokedex").value(4))
                .andExpect(jsonPath("$.identificado.nombre").value("charmander"))
                .andExpect(jsonPath("$.identificado.nombreEs").value("Charmander"))
                .andExpect(jsonPath("$.identificado.totalEstadisticas").value(309))
                .andExpect(jsonPath("$.identificado.imagenUrl").value("https://img.test/4.png"))
                // tipos con la forma [{ id, nombre }], como el resto de la API
                .andExpect(jsonPath("$.identificado.tipos", hasSize(1)))
                .andExpect(jsonPath("$.identificado.tipos[0].id").isNumber())
                .andExpect(jsonPath("$.identificado.tipos[0].nombre").value("fire"))
                .andExpect(jsonPath("$.confianza").value(0.93))
                .andExpect(jsonPath("$.similares", hasSize(3)))
                .andExpect(jsonPath("$.similares[*].idPokedex", not(org.hamcrest.Matchers.hasItem(4))))
                .andExpect(jsonPath("$.similares[0].motivo").isNotEmpty());
        assertThat(llamadasDeVision()).isEqualTo(1);
    }

    @Test
    void similaresComparTenTipoYVanDeMenorAMayorDiferenciaDeTotal() throws Exception {
        mvc.perform(multipart(RUTA).file(jpeg()).with(ip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.similares[*].idPokedex", contains(155, 390, 255)))
                .andExpect(jsonPath("$.similares[*].totalEstadisticas", contains(309, 309, 310)))
                .andExpect(jsonPath("$.similares[*].tipos[0].nombre", contains("fire", "fire", "fire")))
                .andExpect(jsonPath("$.similares[0].motivo").value("Comparte el tipo Fuego y tiene el mismo total"))
                .andExpect(jsonPath("$.similares[2].motivo").value("Comparte el tipo Fuego y su total difiere en 1"));
    }

    @Test
    void sinTokenResponde200() throws Exception {
        // Ni cabecera Authorization ni usuario autenticado
        mvc.perform(multipart(RUTA).file(jpeg()).with(ip))
                .andExpect(status().isOk());
    }

    @Test
    void elSlugSeBuscaSinImportarMayusculasNiTildes() throws Exception {
        vision.responder(new ResultadoVision(true, "CHARMÁNDER", 0.8));

        mvc.perform(multipart(RUTA).file(jpeg()).with(ip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificado.idPokedex").value(4));
    }

    @Test
    void confianzaExactamenteEnElUmbralSeAcepta() throws Exception {
        // "menor a 0,6" es 422; 0,6 justo, no
        vision.responder(new ResultadoVision(true, "charmander", 0.6));

        mvc.perform(multipart(RUTA).file(jpeg()).with(ip))
                .andExpect(status().isOk());
    }

    @Test
    void aceptaPngYWebpPorSusPrimerosBytes() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        byte[] webp = {'R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P', 'V'};

        mvc.perform(multipart(RUTA).file(conBytes("a.png", "image/png", png)).with(ip))
                .andExpect(status().isOk());
        mvc.perform(multipart(RUTA).file(conBytes("a.webp", "image/webp", webp)).with(ip))
                .andExpect(status().isOk());
    }

    // ---------- 400 (la visión no se llama) ----------

    @Test
    void sinElCampoFotoResponde400() throws Exception {
        mvc.perform(multipart(RUTA).with(ip))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.mensaje").value("Envía una foto en el campo foto"))
                .andExpect(jsonPath("$.path").value(RUTA));
        assertThat(llamadasDeVision()).isZero();
    }

    @Test
    void conUnCampoConOtroNombreResponde400() throws Exception {
        var otra = new MockMultipartFile("imagen", "foto.jpg", "image/jpeg", jpeg().getBytes());

        mvc.perform(multipart(RUTA).file(otra).with(ip))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Envía una foto en el campo foto"));
        assertThat(llamadasDeVision()).isZero();
    }

    @Test
    void fotoVaciaResponde400() throws Exception {
        mvc.perform(multipart(RUTA).file(conBytes("foto.jpg", "image/jpeg", new byte[0])).with(ip))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Envía una foto en el campo foto"));
        assertThat(llamadasDeVision()).isZero();
    }

    @Test
    void pdfResponde400() throws Exception {
        byte[] pdf = "%PDF-1.7\n1 0 obj".getBytes(StandardCharsets.US_ASCII);

        mvc.perform(multipart(RUTA).file(conBytes("doc.pdf", "application/pdf", pdf)).with(ip))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La foto debe ser JPG, PNG o WEBP"));
        assertThat(llamadasDeVision()).isZero();
    }

    @Test
    void txtRenombradoAJpgResponde400AunqueDigaImageJpeg() throws Exception {
        byte[] texto = "esto no es una imagen".getBytes(StandardCharsets.UTF_8);

        mvc.perform(multipart(RUTA).file(conBytes("falsa.jpg", "image/jpeg", texto)).with(ip))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La foto debe ser JPG, PNG o WEBP"));
        assertThat(llamadasDeVision()).isZero();
    }

    @Test
    void fotoDeMasDe5MbResponde400NoUn500() throws Exception {
        byte[] enorme = new byte[5 * 1024 * 1024 + 1];
        enorme[0] = (byte) 0xFF;
        enorme[1] = (byte) 0xD8;
        enorme[2] = (byte) 0xFF;

        mvc.perform(multipart(RUTA).file(conBytes("grande.jpg", "image/jpeg", enorme)).with(ip))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La foto pesa más de 5 MB"));
        assertThat(llamadasDeVision()).isZero();
    }

    // ---------- 422 ----------

    @Test
    void sinPokemonEnLaFotoResponde422() throws Exception {
        vision.responder(new ResultadoVision(false, null, 0.0));

        mvc.perform(multipart(RUTA).file(jpeg()).with(ip))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_NO_RECONOCIDO));
    }

    @Test
    void confianzaDe05Responde422() throws Exception {
        vision.responder(new ResultadoVision(true, "charmander", 0.5));

        mvc.perform(multipart(RUTA).file(jpeg()).with(ip))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_NO_RECONOCIDO));
    }

    @Test
    void slugQueNoExisteEnLaBaseResponde422() throws Exception {
        vision.responder(new ResultadoVision(true, "missingno", 0.95));

        mvc.perform(multipart(RUTA).file(jpeg()).with(ip))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.mensaje").value(MENSAJE_NO_RECONOCIDO));
    }

    // ---------- 502 ----------

    @Test
    void siLaVisionFallaResponde502ConMensajeGenerico() throws Exception {
        vision.fallar();

        mvc.perform(multipart(RUTA).file(jpeg()).with(ip))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.mensaje").value("El reconocimiento no está disponible en este momento"))
                .andExpect(jsonPath("$.mensaje", not(containsString("http"))))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    // ---------- 429 ----------

    @Test
    void laPeticion11DeLaMismaIpResponde429YNoLlamaALaVision() throws Exception {
        for (int i = 1; i <= 10; i++) {
            mvc.perform(multipart(RUTA).file(jpeg()).with(ip))
                    .andExpect(status().isOk());
        }
        assertThat(llamadasDeVision()).isEqualTo(10);

        mvc.perform(multipart(RUTA).file(jpeg()).with(ip))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.mensaje").value("Demasiadas fotos seguidas. Espera un minuto"));

        assertThat(llamadasDeVision()).isEqualTo(10);
    }

    @Test
    void elLimiteEsPorIpYOtraIpSigueFuncionando() throws Exception {
        for (int i = 0; i < 10; i++) {
            mvc.perform(multipart(RUTA).file(jpeg()).with(ip)).andExpect(status().isOk());
        }
        mvc.perform(multipart(RUTA).file(jpeg()).with(ip)).andExpect(status().isTooManyRequests());

        mvc.perform(multipart(RUTA).file(jpeg()).with(peticion -> {
            peticion.setRemoteAddr("10.99.99.99");
            return peticion;
        })).andExpect(status().isOk());
    }
}
