package bio.prodesp.deduplicacao.service.audit;

import bio.prodesp.deduplicacao.commons.model.enums.StatusValidacao;
import bio.prodesp.deduplicacao.repository.AuditRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Implementação do serviço de auditoria
 *
 * <p>Utiliza AuditRepository para persistir eventos em OpenSearch.
 * Todas as operações são assíncronas para não impactar performance do fluxo principal.
 *
 * @author Bio Sanitização Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditRepository auditRepository;

    @Override
    @Async
    public void registrar(String evento, String cpf, String idColeta, String detalhes) {
        try {
            auditRepository.registrarInfo(evento, cpf, idColeta, detalhes);
            log.debug("Auditoria registrada: {} - CPF: {}, ID: {}", evento, cpf, idColeta);
        } catch (Exception e) {
            // Nunca falha o fluxo principal por erro de auditoria
            log.error("Falha ao registrar auditoria - Evento: {}, CPF: {}", evento, cpf, e);
        }
    }

    @Override
    @Async
    public void registrarMudancaStatus(
            String cpf,
            String idColeta,
            StatusValidacao statusAnterior,
            StatusValidacao statusNovo,
            String motivo) {

        try {
            String detalhes = String.format(
                "{\"statusAnterior\":\"%s\",\"statusNovo\":\"%s\",\"motivo\":\"%s\"}",
                statusAnterior != null ? statusAnterior : "null",
                statusNovo,
                motivo != null ? motivo : ""
            );

            auditRepository.registrarInfo("MUDANCA_STATUS", cpf, idColeta, detalhes);

            log.debug("Mudança de status auditada - CPF: {}, {} -> {}",
                    cpf, statusAnterior, statusNovo);
        } catch (Exception e) {
            log.error("Falha ao auditar mudança de status - CPF: {}", cpf, e);
        }
    }

    @Override
    @Async
    public void registrarCadastroABIS(
            String cpf,
            String idColeta,
            String abisEncounterId,
            String operacao) {

        try {
            String detalhes = String.format(
                "{\"encounterId\":\"%s\",\"operacao\":\"%s\"}",
                abisEncounterId,
                operacao
            );

            auditRepository.registrarInfo("CADASTRO_ABIS", cpf, idColeta, detalhes);

            log.debug("Cadastro ABIS auditado - CPF: {}, Encounter: {}, Op: {}",
                    cpf, abisEncounterId, operacao);
        } catch (Exception e) {
            log.error("Falha ao auditar cadastro ABIS - CPF: {}", cpf, e);
        }
    }

    @Override
    @Async
    public void registrarErro(
            String evento,
            String cpf,
            String idColeta,
            Exception exception) {

        try {
            String detalhes = String.format(
                "{\"erro\":\"%s\",\"mensagem\":\"%s\",\"stackTrace\":\"%s\"}",
                exception.getClass().getSimpleName(),
                exception.getMessage() != null ? exception.getMessage().replace("\"", "'") : "",
                exception.getStackTrace().length > 0 ? exception.getStackTrace()[0].toString() : ""
            );

            auditRepository.registrarErro("ERRO_" + evento, cpf, idColeta, detalhes);

            log.debug("Erro auditado - Evento: {}, CPF: {}, Erro: {}",
                    evento, cpf, exception.getClass().getSimpleName());
        } catch (Exception e) {
            log.error("Falha crítica ao auditar erro - Evento: {}", evento, e);
        }
    }

    @Override
    @Async
    public void registrarComDuracao(
            String evento,
            String cpf,
            String idColeta,
            long duracaoMs,
            String detalhes) {

        try {
            auditRepository.registrarComDuracao(evento, cpf, idColeta, duracaoMs, detalhes);

            log.debug("Evento com duração auditado - Evento: {}, CPF: {}, Duração: {}ms",
                    evento, cpf, duracaoMs);
        } catch (Exception e) {
            log.error("Falha ao auditar evento com duração - Evento: {}", evento, e);
        }
    }
}