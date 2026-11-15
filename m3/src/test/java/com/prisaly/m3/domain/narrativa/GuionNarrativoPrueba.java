package com.prisaly.m3.domain.narrativa;

/** Guion de PRUEBA (no oficial, sin contenido académico aprobado) en formato prisaly-guion-1. */
public final class GuionNarrativoPrueba {
    private GuionNarrativoPrueba() { }

    public static final String VALIDO = """
            {"formato":"prisaly-guion-1","inicio":"recepcion",
             "espacios":[
               {"idEspacio":"recepcion","titulo":"Recepción (prueba)","descripcion":"Espacio de prueba.",
                "informaciones":[{"idInformacion":"cartel","texto":"Texto informativo de prueba."}],
                "accesos":[{"destino":"sala","texto":"Ir a la sala"}]},
               {"idEspacio":"sala","titulo":"Sala (prueba)",
                "accesos":[{"destino":"recepcion","texto":"Volver"}]}],
             "personajes":[{"idPersonaje":"persona-a","nombre":"Persona A","rol":"Personaje de prueba",
                            "estadoInicial":"esperando","expresionInicial":"serena"}],
             "intervenciones":[
               {"idIntervencion":"saludo","idEspacio":"recepcion","idPersonaje":"persona-a",
                "dialogos":[{"idPersonaje":"persona-a","texto":"Hola, esto es una conversación de prueba."}],
                "condicion":{"marca":"visto-cartel","valor":false},
                "consecuencias":[{"tipo":"marca","marca":"saludado","valor":true},
                                 {"tipo":"personaje","idPersonaje":"persona-a","estado":"atendiendo","expresion":"alegre"}]}]}
            """;
}
