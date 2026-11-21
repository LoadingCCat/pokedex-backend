package com.pokedex.service;

import com.pokedex.exception.FotoFaltanteException;
import com.pokedex.exception.FotoFormatoInvalidoException;
import com.pokedex.exception.FotoMuyPesadaException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * F20: valida la foto en el servidor, antes de gastar una llamada a la IA.
 * El tipo se decide por los primeros bytes del archivo; el {@code Content-Type} y la
 * extensión los escribe el cliente y se ignoran por completo.
 */
@Component
public class ValidadorFoto {

    /** 5 MB: el máximo que permite el contrato. */
    public static final long MAXIMO_BYTES = 5L * 1024 * 1024;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};
    private static final int BYTES_A_LEER = 12;

    /**
     * @return el tipo real de la imagen: {@code image/jpeg}, {@code image/png} o {@code image/webp}
     * @throws FotoFaltanteException        si no hay archivo o está vacío
     * @throws FotoMuyPesadaException       si pesa más de 5 MB
     * @throws FotoFormatoInvalidoException si no es JPEG, PNG ni WEBP
     */
    public String validar(MultipartFile foto) {
        if (foto == null || foto.isEmpty()) {
            throw new FotoFaltanteException();
        }
        if (foto.getSize() > MAXIMO_BYTES) {
            throw new FotoMuyPesadaException();
        }
        byte[] inicio = leerInicio(foto);
        if (empiezaCon(inicio, JPEG, 0)) {
            return "image/jpeg";
        }
        if (empiezaCon(inicio, PNG, 0)) {
            return "image/png";
        }
        if (empiezaCon(inicio, RIFF, 0) && empiezaCon(inicio, WEBP, 8)) {
            return "image/webp";
        }
        throw new FotoFormatoInvalidoException();
    }

    private static byte[] leerInicio(MultipartFile foto) {
        try (InputStream entrada = foto.getInputStream()) {
            return entrada.readNBytes(BYTES_A_LEER);
        } catch (IOException e) {
            // Un archivo que no se puede leer no es una imagen válida; sin detalle al cliente.
            throw new FotoFormatoInvalidoException();
        }
    }

    private static boolean empiezaCon(byte[] datos, byte[] firma, int desde) {
        if (datos.length < desde + firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if (datos[desde + i] != firma[i]) {
                return false;
            }
        }
        return true;
    }
}
