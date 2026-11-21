# F20 · Identificar un Pokémon por foto y ver similares

**Estado:** Pendiente

## Historia
Como jugador o fan, quiero subir o tomar una foto de un Pokémon (una carta, una pantalla, un peluche, una figura) para saber cuál es y ver otros 3 parecidos, sin tener que adivinar su nombre para buscarlo.

## Comportamiento
1. `POST /api/pokemons/identificar` recibe `multipart/form-data` con un único campo `foto`. Es público: no pide token.
2. El servidor valida la foto **antes** de gastar una llamada a la IA:
   - que el campo exista y no esté vacío;
   - que el tipo sea JPEG, PNG o WEBP, mirando **los primeros bytes del archivo** y no solo la cabecera `Content-Type`, que el cliente puede falsear;
   - que pese como máximo 5 MB.
3. El servidor envía la imagen a un servicio de visión (detrás de la interfaz `ServicioVision`) y le pide una respuesta estructurada: `{ identificado, slug, confianza }`. La clave del servicio vive solo en el servidor, como variable de entorno.
4. Si la IA no reconoce un Pokémon, la confianza es menor que el umbral (0,6), o el slug no existe en la base, responde 422.
5. Si lo reconoce, busca el Pokémon en PostgreSQL por su `nombre` (slug), sin importar mayúsculas ni tildes.
6. Busca **3 similares**:
   - Excluye al identificado.
   - Comparten al menos un tipo con él.
   - Se ordenan por la menor diferencia de `totalEstadisticas`; si empatan, primero los de la misma generación y luego por `idPokedex`.
   - Si hay menos de 3 con tipo compartido, se completan con los de total más cercano, sin importar el tipo.
   - Cada similar dice por qué lo es (`motivo`).
7. La foto **no se guarda** en ningún lado (ni disco ni base). En el log solo queda el resultado: slug, confianza y tiempo.
8. Límite de uso: como es público y cada llamada gasta cuota del servicio de visión, cada IP puede identificar como máximo 10 fotos por minuto. Pasado el límite, responde 429.
9. Frontend:
   - Página `/identificar` con dos opciones: tomar foto con la cámara del celular o elegir un archivo.
   - Antes de enviar, el navegador reduce la imagen (lado mayor de 1024 px, JPEG) para que pese menos de 1,5 MB. Vercel no acepta cuerpos de más de 4,5 MB.
   - Muestra un estado de carga y no permite enviar dos veces seguidas.
   - Muestra el resultado en tarjetas (el identificado y los 3 similares) que llevan a su ficha.
   - Sin `API_URL` (modo mock), la página muestra un aviso en vez de fallar.

## Criterios de aceptación del back
- [ ] **Éxito.** Dado un Pokémon que existe en la base y una foto válida en la que el servicio de visión lo reconoce, cuando se envía a `POST /api/pokemons/identificar`, entonces responde 200 con el Pokémon leído de PostgreSQL y exactamente 3 similares, ninguno igual al identificado.
- [ ] **Similares bien elegidos.** Dado Charmander (tipo fuego, total 309) como resultado, cuando se calculan los similares, entonces los 3 comparten al menos un tipo con él y están ordenados de menor a mayor diferencia de total.
- [ ] **Sin token.** Dada una petición sin la cabecera `Authorization`, cuando se envía una foto válida, entonces responde 200 (no 401).
- [ ] **Sin foto.** Dada una petición sin el campo `foto`, o con un archivo vacío, cuando llega al servidor, entonces responde 400 con un mensaje claro y el servicio de visión no se llama.
- [ ] **Formato inválido.** Dado un `.pdf` o un `.txt` renombrado a `.jpg`, cuando llega al servidor, entonces responde 400 («La foto debe ser JPG, PNG o WEBP») y el servicio de visión no se llama.
- [ ] **Muy pesada.** Dada una foto de más de 5 MB, cuando llega al servidor, entonces responde 400 («La foto pesa más de 5 MB»), no un 500.
- [ ] **No hay Pokémon.** Dada una foto en la que el servicio de visión no reconoce un Pokémon, o lo reconoce con confianza menor a 0,6, cuando se procesa, entonces responde 422 («No reconocimos un Pokémon en la foto. Prueba con otra más clara»).
- [ ] **No está en la base.** Dado que el servicio de visión responde con un slug que no existe en la base, cuando se procesa, entonces responde 422 con el mismo mensaje.
- [ ] **Falla externa.** Dado que el servicio de visión no responde a tiempo (timeout) o devuelve un error, cuando se procesa, entonces responde 502 con un mensaje genérico, sin la clave, la URL del proveedor ni la traza.
- [ ] **Abuso.** Dadas 11 peticiones desde la misma IP en menos de un minuto, cuando llega la número 11, entonces responde 429 y el servicio de visión no se llama.
- [ ] **Sin rastro.** Después de cualquier petición, la foto no queda guardada en disco ni en la base, y el log no contiene la imagen.

## Criterios de aceptación del front
- [ ] En el celular, `/identificar` deja tomar una foto con la cámara trasera o elegir un archivo de la galería.
- [ ] Una foto de 8 MB se reduce en el navegador y se envía con menos de 1,5 MB.
- [ ] Mientras se analiza, se ve un estado de carga y el botón no permite un segundo envío.
- [ ] Con un resultado 200, se ven el Pokémon identificado y 3 tarjetas de similares; cada tarjeta lleva a su ficha.
- [ ] Los errores 400, 422, 429 y 502 muestran el mensaje del contrato, sin códigos ni textos técnicos.
- [ ] A 360 px de ancho no hay scroll horizontal.
- [ ] Sin `API_URL`, `/identificar` muestra el aviso y no falla.

## Fuera de alcance
- Reconocer formas regionales, megaevoluciones o shiny como entradas distintas: se devuelve el Pokémon base (F16 sigue pendiente).
- Varias fotos a la vez o varios Pokémon en una misma foto: se identifica el más visible.
- Guardar un historial de identificaciones.
- Entrenar o ajustar un modelo propio: se usa un servicio de visión externo.
