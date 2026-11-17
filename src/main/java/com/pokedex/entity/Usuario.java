package com.pokedex.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Usuario que puede iniciar sesión en el panel de administración (F15).
 * La contraseña se guarda con BCrypt; nunca en claro. El administrador inicial se crea al
 * arrancar desde ADMIN_USERNAME y ADMIN_PASSWORD (AdministradorInicial).
 */
@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    public static final String ROL_ADMIN = "ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(nullable = false, unique = true, length = 50)
    private String usuario;

    // Hash BCrypt (60 caracteres)
    @JsonIgnore
    @Column(nullable = false, length = 100)
    private String contrasena;

    @Column(nullable = false, length = 20)
    private String rol;

    public Usuario(String usuario, String contrasenaCifrada, String rol) {
        this.usuario = usuario;
        this.contrasena = contrasenaCifrada;
        this.rol = rol;
    }
}
