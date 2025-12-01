package bio.prodesp.deduplicacao.util;

import lombok.experimental.UtilityClass;

/**
 * Utilitário para validação de CPF com dígitos verificadores
 *
 * <p>Implementa validação completa conforme regras da Receita Federal:
 * - Remove caracteres não numéricos
 * - Valida tamanho (11 dígitos)
 * - Rejeita CPFs com todos os dígitos iguais (ex: 111.111.111-11)
 * - Valida dígitos verificadores (módulo 11)
 *
 * @author Bio Sanitização Team
 * @since 1.0.0
 */
@UtilityClass
public class CpfValidator {

    /**
     * Valida se o CPF é válido
     *
     * @param cpf CPF formatado ou não (ex: "123.456.789-10" ou "12345678910")
     * @return true se válido, false caso contrário
     */
    public static boolean isValid(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            return false;
        }

        // Remove caracteres não numéricos
        String cpfNumeros = cpf.replaceAll("\\D", "");

        // Valida tamanho
        if (cpfNumeros.length() != 11) {
            return false;
        }

        // Rejeita CPFs com todos os dígitos iguais
        if (cpfNumeros.matches("(\\d)\\1{10}")) {
            return false;
        }

        // Valida dígitos verificadores
        return validarDigitoVerificador(cpfNumeros, 9)
            && validarDigitoVerificador(cpfNumeros, 10);
    }

    /**
     * Valida um dígito verificador do CPF usando o algoritmo de módulo 11
     *
     * @param cpf CPF com 11 dígitos numéricos
     * @param posicao Posição do dígito a validar (9 = primeiro DV, 10 = segundo DV)
     * @return true se o dígito verificador está correto
     */
    private static boolean validarDigitoVerificador(String cpf, int posicao) {
        int soma = 0;
        int peso = posicao + 1;

        // Calcula soma ponderada
        for (int i = 0; i < posicao; i++) {
            int digito = Character.getNumericValue(cpf.charAt(i));
            soma += digito * peso;
            peso--;
        }

        // Calcula resto da divisão por 11
        int resto = soma % 11;
        int digitoEsperado = resto < 2 ? 0 : 11 - resto;

        // Compara com o dígito verificador informado
        int digitoInformado = Character.getNumericValue(cpf.charAt(posicao));

        return digitoEsperado == digitoInformado;
    }

    /**
     * Remove formatação do CPF (mantém apenas números)
     *
     * @param cpf CPF formatado
     * @return CPF com apenas números ou null se entrada for null
     */
    public static String removerFormatacao(String cpf) {
        if (cpf == null) {
            return null;
        }
        return cpf.replaceAll("\\D", "");
    }

    /**
     * Formata CPF para o padrão 999.999.999-99
     *
     * @param cpf CPF com 11 dígitos
     * @return CPF formatado ou string original se inválido
     */
    public static String formatar(String cpf) {
        if (cpf == null) {
            return null;
        }

        String cpfNumeros = removerFormatacao(cpf);

        if (cpfNumeros.length() != 11) {
            return cpf; // Retorna original se não tiver 11 dígitos
        }

        return String.format("%s.%s.%s-%s",
            cpfNumeros.substring(0, 3),
            cpfNumeros.substring(3, 6),
            cpfNumeros.substring(6, 9),
            cpfNumeros.substring(9, 11)
        );
    }

    /**
     * Mascara CPF para logs (mostra apenas últimos 4 dígitos)
     *
     * @param cpf CPF a ser mascarado
     * @return CPF mascarado no formato ***.***.*NN-NN
     */
    public static String mascararParaLog(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            return "***";
        }

        String cpfNumeros = removerFormatacao(cpf);

        if (cpfNumeros.length() != 11) {
            return "***";
        }

        return String.format("***.***.*%s-%s",
            cpfNumeros.substring(7, 9),
            cpfNumeros.substring(9, 11)
        );
    }
}
