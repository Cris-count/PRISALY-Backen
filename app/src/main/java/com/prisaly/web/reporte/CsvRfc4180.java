package com.prisaly.web.reporte;

import java.util.List;

/**
 * DC-BE-CIERRE-FUNCIONAL-72 (RF-16): minimal RFC 4180 writer with spreadsheet formula-injection protection.
 *
 * <p>Every cell that starts with {@code = + - @}, TAB or CR is prefixed with {@code '} so spreadsheet software treats
 * it as text (OWASP "CSV Injection"). Afterwards a cell containing a comma, a double quote, CR or LF is enclosed in
 * double quotes and its double quotes are doubled. Records end with CRLF. {@code null} is written as an empty cell.
 */
final class CsvRfc4180 {
    static final String FIN_REGISTRO = "\r\n";

    private CsvRfc4180() {
    }

    static String celda(String valor) {
        if (valor == null || valor.isEmpty()) {
            return "";
        }
        String texto = neutralizarFormula(valor);
        boolean comillas = texto.indexOf(',') >= 0 || texto.indexOf('"') >= 0 || texto.indexOf('\r') >= 0
                || texto.indexOf('\n') >= 0;
        return comillas ? '"' + texto.replace("\"", "\"\"") + '"' : texto;
    }

    static String neutralizarFormula(String valor) {
        char primero = valor.charAt(0);
        boolean peligroso = primero == '=' || primero == '+' || primero == '-' || primero == '@' || primero == '\t'
                || primero == '\r';
        return peligroso ? "'" + valor : valor;
    }

    static void registro(StringBuilder destino, List<String> celdas) {
        for (int i = 0; i < celdas.size(); i++) {
            if (i > 0) {
                destino.append(',');
            }
            destino.append(celda(celdas.get(i)));
        }
        destino.append(FIN_REGISTRO);
    }
}
