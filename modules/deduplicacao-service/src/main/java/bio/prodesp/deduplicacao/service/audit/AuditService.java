package bio.prodesp.deduplicacao.service.audit;

import bio.prodesp.deduplicacao.commons.model.enums.StatusValidacao;

/**
 * Serviço de auditoria para rastreabilidade de operações no sistema de deduplicação
 *
 * <p>Garante compliance com LGPD através de audit trail completo.
 * Todas as operações críticas devem ser auditadas através deste serviço.
 *
 * <p>Características:
 * <ul>
 *   <li>Execução assíncrona (não bloqueia fluxo principal)</li>
 *   <li>Fail-safe: erros de auditoria não afetam processamento</li>
 *   <li>Armazenamento em OpenSearch com rollover diário</li>
 *   <li>Retenção automática via ILM policy</li>
 * </ul>
 *
 * @author Bio Sanitização Team
 * @since 1.0.0
 */
public interface AuditService {

    /**
     * Registra evento genérico de auditoria
     *
     * @param evento Tipo do evento (ex: PROCESSAMENTO_CONCLUIDO, ERRO_OSIA)
     * @param cpf CPF do cidadão
     * @param idColeta ID da coleta biométrica
     * @param detalhes Detalhes adicionais do evento
     */
    void registrar(String evento, String cpf, String idColeta, String detalhes);

    /**
     * Registra mudança de status de validação
     *
     * @param cpf CPF do cidadão
     * @param idColeta ID da coleta
     * @param statusAnterior Status antes da mudança (pode ser null)
     * @param statusNovo Status após a mudança
     * @param motivo Motivo da mudança de status
     */
    void registrarMudancaStatus(
        String cpf,
        String idColeta,
        StatusValidacao statusAnterior,
        StatusValidacao statusNovo,
        String motivo
    );

    /**
     * Registra operação de cadastro no ABIS/OSIA
     *
     * @param cpf CPF do cidadão
     * @param idColeta ID da coleta
     * @param abisEncounterId ID do encounter criado no ABIS
     * @param operacao Tipo de operação (ENROLL, UPDATE, DELETE)
     */
    void registrarCadastroABIS(
        String cpf,
        String idColeta,
        String abisEncounterId,
        String operacao
    );

    /**
     * Registra erro/falha durante processamento
     *
     * @param evento Tipo do evento que causou o erro
     * @param cpf CPF do cidadão
     * @param idColeta ID da coleta
     * @param exception Exceção capturada
     */
    void registrarErro(
        String evento,
        String cpf,
        String idColeta,
        Exception exception
    );

    /**
     * Registra evento com medição de duração (para análise de performance)
     *
     * @param evento Tipo do evento
     * @param cpf CPF do cidadão
     * @param idColeta ID da coleta
     * @param duracaoMs Duração da operação em milissegundos
     * @param detalhes Detalhes adicionais
     */
    void registrarComDuracao(
        String evento,
        String cpf,
        String idColeta,
        long duracaoMs,
        String detalhes
    );
}