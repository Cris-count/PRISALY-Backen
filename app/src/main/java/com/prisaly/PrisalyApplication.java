package com.prisaly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Punto de arranque del monolito. Levanta los módulos m1 a m7 bajo el contexto /api. */
@SpringBootApplication
public class PrisalyApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrisalyApplication.class, args);
    }
}
