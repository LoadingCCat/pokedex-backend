package com.pokedex.controller;

import com.pokedex.ConSeguridad;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** F20, tarea 2.1: GET /health y la regla pública de POST /pokemons/identificar. */
@WebMvcTest(controllers = HealthController.class)
@ConSeguridad
class HealthControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void healthResponde200SinTokenYSinConsultarLaBase() throws Exception {
        // El contexto no tiene ningún repositorio: si health tocara la base, no arrancaría.
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ok"));
    }

    @Test
    void identificarSinTokenNoPideAutenticacion() throws Exception {
        // El controlador llega después (tarea 2.5): mientras tanto sirve 404 o 405, nunca 401.
        var foto = new MockMultipartFile("foto", "foto.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mvc.perform(multipart("/api/pokemons/identificar").file(foto))
                .andExpect(status().is(anyOf(is(404), is(405))));
    }
}
