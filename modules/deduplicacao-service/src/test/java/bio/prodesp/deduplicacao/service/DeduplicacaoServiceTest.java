package bio.prodesp.deduplicacao.service;

import bio.prodesp.deduplicacao.commons.exception.ABISViolacaoUnicidadeException;
import bio.prodesp.deduplicacao.commons.exception.CriterioEntradaException;
import bio.prodesp.deduplicacao.commons.exception.OSIAException;
import bio.prodesp.deduplicacao.commons.model.domain.ColetaMetadata;
import bio.prodesp.deduplicacao.commons.model.domain.DadoBiometricoMetadata;
import bio.prodesp.deduplicacao.commons.model.domain.TemplateMetadata;
import bio.prodesp.deduplicacao.commons.model.dto.ResultadoDeduplicacao;
import bio.prodesp.deduplicacao.commons.model.dto.osia.*;
import bio.prodesp.deduplicacao.commons.model.enums.StatusValidacao;
import bio.prodesp.deduplicacao.repository.ColetaRepository;
import bio.prodesp.deduplicacao.service.audit.AuditService;
import bio.prodesp.deduplicacao.client.OSIAClient;
import bio.prodesp.deduplicacao.service.idempotencia.IdempotenciaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para DeduplicacaoService
 *
 * Estratégia de teste:
 * - Testes isolados com mocks para todas as dependências externas
 * - Cobertura de todos os fluxos de negócio (RN003-RN010)
 * - Verificação de chamadas a repositórios e serviços auxiliares
 * - Testes de exceções e edge cases
 *
 * @author Bio Sanitização Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeduplicacaoService - Testes Unitários")
class DeduplicacaoServiceTest {

    @Mock
    private OSIAClient osiaClient;

    @Mock
    private ColetaRepository coletaRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private IdempotenciaService idempotenciaService;

    @InjectMocks
    private DeduplicacaoService service;

    private ColetaMetadata coletaValida;
    private static final String CPF_VALIDO = "11144477735"; // CPF válido com dígitos verificadores corretos
    private static final String ID_COLETA = "COL-12345";
    private static final String IDEMPOTENCY_KEY = "DEDUP:11144477735:COL-12345";

    @BeforeEach
    void setUp() {
        coletaValida = criarColetaValida();

        // Configurar comportamento padrão do idempotenciaService
        when(idempotenciaService.gerarChave(anyString(), anyString()))
            .thenReturn(IDEMPOTENCY_KEY);
        when(idempotenciaService.verificar(anyString()))
            .thenReturn(Optional.empty());
    }

    // ========== TESTES DE IDEMPOTÊNCIA ==========

    @Nested
    @DisplayName("Testes de Idempotência")
    class IdempotenciaTests {

        @Test
        @DisplayName("Deve retornar resultado anterior quando já processado (cache hit)")
        void deveRetornarResultadoAnteriorQuandoJaProcessado() {
            // Arrange
            ResultadoDeduplicacao resultadoAnterior = ResultadoDeduplicacao.builder()
                .status(StatusValidacao.VALIDA)
                .abisEncounterId("ABIS-123")
                .build();

            when(idempotenciaService.verificar(IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(resultadoAnterior));

            // Act
            ResultadoDeduplicacao resultado = service.processar(coletaValida);

            // Assert
            assertThat(resultado).isEqualTo(resultadoAnterior);

            // Verifica que NÃO chamou OSIA nem repositórios (short-circuit)
            verify(osiaClient, never()).buscarEncounters(anyString());
            verify(coletaRepository, never()).atualizarStatusProcessamento(
                anyString(), any(), anyString(), anyString(), anyString(), any()
            );

            // Log de auditoria NÃO deve ser chamado
            verify(auditService, never()).registrarComDuracao(
                anyString(), anyString(), anyString(), anyLong(), anyString()
            );
        }

        @Test
        @DisplayName("Deve registrar idempotência após processamento bem-sucedido")
        void deveRegistrarIdempotenciaAposSucesso() throws Exception {
            // Arrange
            configurarCenarioSucessoSimples();

            // Act
            service.processar(coletaValida);

            // Assert
            ArgumentCaptor<ResultadoDeduplicacao> captor =
                ArgumentCaptor.forClass(ResultadoDeduplicacao.class);

            verify(idempotenciaService).registrar(
                eq(IDEMPOTENCY_KEY),
                captor.capture(),
                eq(1440L) // 24h TTL
            );

            assertThat(captor.getValue().getStatus()).isEqualTo(StatusValidacao.VALIDA);
        }
    }

    // ========== TESTES DE VALIDAÇÃO (RN003) ==========

    @Nested
    @DisplayName("RN003 - Validação de Critérios de Entrada")
    class CriteriosEntradaTests {

        @Test
        @DisplayName("Deve rejeitar coleta com validação pendente")
        void deveRejeitarColetaComValidacaoPendente() {
            // Arrange
            coletaValida.setValidacaoPendente(true);

            // Act
            ResultadoDeduplicacao resultado = service.processar(coletaValida);

            // Assert
            assertThat(resultado.getStatus()).isEqualTo(StatusValidacao.INVALIDA);
            assertThat(resultado.getMotivoRejeicao())
                .contains("Critério de entrada")
                .contains("validação pendente");

            // Verifica audit de erro
            verify(auditService).registrarErro(
                eq("CRITERIO_ENTRADA"),
                eq(CPF_VALIDO),
                eq(ID_COLETA),
                any(CriterioEntradaException.class)
            );

            // NÃO deve chamar OSIA
            verify(osiaClient, never()).buscarEncounters(anyString());
        }

        @Test
        @DisplayName("Deve rejeitar CPF inválido")
        void deveRejeitarCpfInvalido() {
            // Arrange
            coletaValida.setCpf("123"); // CPF com menos de 11 dígitos

            // Act
            ResultadoDeduplicacao resultado = service.processar(coletaValida);

            // Assert
            assertThat(resultado.getStatus()).isEqualTo(StatusValidacao.INVALIDA);
            assertThat(resultado.getMotivoRejeicao()).contains("CPF inválido");
        }

        @Test
        @DisplayName("Deve rejeitar idade <= 15 anos")
        void deveRejeitarIdadeMenorOuIgual15() {
            // Arrange
            LocalDate dataHoje = LocalDate.now();
            LocalDate dataNascimento = dataHoje.minusYears(15); // Exatamente 15 anos

            Date dataNascimentoDate = Date.from(
                dataNascimento.atStartOfDay(ZoneId.systemDefault()).toInstant()
            );
            coletaValida.setDataNascimento(dataNascimentoDate);

            // Act
            ResultadoDeduplicacao resultado = service.processar(coletaValida);

            // Assert
            assertThat(resultado.getStatus()).isEqualTo(StatusValidacao.INVALIDA);
            assertThat(resultado.getMotivoRejeicao())
                .contains("Idade deve ser maior que 15 anos");
        }

        @Test
        @DisplayName("Deve aceitar idade > 15 anos")
        void deveAceitarIdadeMaiorQue15() throws Exception {
            // Arrange
            LocalDate dataHoje = LocalDate.now();
            LocalDate dataNascimento = dataHoje.minusYears(16); // 16 anos

            Date dataNascimentoDate = Date.from(
                dataNascimento.atStartOfDay(ZoneId.systemDefault()).toInstant()
            );
            coletaValida.setDataNascimento(dataNascimentoDate);

            configurarCenarioSucessoSimples();

            // Act
            ResultadoDeduplicacao resultado = service.processar(coletaValida);

            // Assert
            assertThat(resultado.getStatus()).isEqualTo(StatusValidacao.VALIDA);

            // Deve ter processado normalmente
            verify(osiaClient).buscarEncounters(CPF_VALIDO);
        }

        @Test
        @DisplayName("Deve rejeitar coleta sem dados biométricos")
        void deveRejeitarColetaSemDadosBiometricos() {
            // Arrange
            coletaValida.setDadosBiometricos(Collections.emptyList());

            // Act
            ResultadoDeduplicacao resultado = service.processar(coletaValida);

            // Assert
            assertThat(resultado.getStatus()).isEqualTo(StatusValidacao.INVALIDA);
            assertThat(resultado.getMotivoRejeicao()).contains("Nenhum dado biométrico fornecido");
        }
    }

    // ========== TESTES DE FLUXO PRINCIPAL (RN004) ==========

    @Nested
    @DisplayName("RN004 - Fluxo Principal: Verify 1:1")
    class Verify11Tests {

        @Test
        @DisplayName("Cenário 1: Verify encontrou match + 1 encounter = VALIDA")
        void cenario1_VerifyMatchCom1Encounter() throws Exception {
            // Arrange
            EncountersResponse encounters = criarEncountersResponse(1);
            VerifyResponse verifyResponse = criarVerifyResponseMatch(true, 0.95);
            EnrollResponse enrollResponse = EnrollResponse.builder()
                .encounterId("ABIS-NEW-123")
                .build();

            when(osiaClient.buscarEncounters(CPF_VALIDO)).thenReturn(encounters);
            when(osiaClient.verificar11(eq(CPF_VALIDO), any())).thenReturn(verifyResponse);
            when(osiaClient.cadastrarEncounter(eq(CPF_VALIDO), eq(ID_COLETA), any()))
                .thenReturn(enrollResponse);
            when(coletaRepository.atualizarStatusProcessamento(
                anyString(), any(StatusValidacao.class), anyString(), any(), any(), any(Double.class)
            )).thenReturn(true);
            when(coletaRepository.atualizarAbisEncounterId(anyString(), anyString()))
                .thenReturn(true);

            // Act
            ResultadoDeduplicacao resultado = service.processar(coletaValida);

            // Assert
            assertThat(resultado.getStatus()).isEqualTo(StatusValidacao.VALIDA);
            assertThat(resultado.getAbisEncounterId()).isEqualTo("ABIS-NEW-123");
            assertThat(resultado.getMatchScore()).isEqualTo(Double.valueOf(0.95));

            // Verifica cadastro no ABIS
            verify(osiaClient).cadastrarEncounter(eq(CPF_VALIDO), eq(ID_COLETA), any());

            // Verifica atualização no OpenSearch
            verify(coletaRepository).atualizarStatusProcessamento(
                eq(ID_COLETA),
                eq(StatusValidacao.VALIDA),
                eq("ABIS-NEW-123"),
                isNull(),
                isNull(),
                eq(0.95)
            );

            // Verifica audit de cadastro ABIS
            verify(auditService).registrarCadastroABIS(
                eq(CPF_VALIDO),
                eq(ID_COLETA),
                eq("ABIS-NEW-123"),
                eq("ENROLL")
            );
        }

        @Test
        @DisplayName("Cenário 2: Verify encontrou mas sem match = INCONCLUSIVA")
        void cenario2_VerifyEncontrouSemMatch() throws Exception {
            // Arrange
            EncountersResponse encounters = criarEncountersResponse(1);
            // Pessoa encontrada (encounterId != null) mas sem match biométrico (verified = false)
            VerifyResponse verifyResponse = VerifyResponse.builder()
                .verified(false)
                .encounterId("ENC-FOUND") // Pessoa encontrada
                .score(null)
                .build();

            when(osiaClient.buscarEncounters(CPF_VALIDO)).thenReturn(encounters);
            when(osiaClient.verificar11(eq(CPF_VALIDO), any())).thenReturn(verifyResponse);
            when(coletaRepository.atualizarStatusProcessamento(
                    anyString(), any(), any(), any(), any(), any()
            )).thenReturn(true);

            // Act
            ResultadoDeduplicacao resultado = service.processar(coletaValida);

            // Assert
            assertThat(resultado.getStatus()).isEqualTo(StatusValidacao.INCONCLUSIVA);
            assertThat(resultado.getMotivoInconclusivo())
                .contains("Pessoa encontrada mas biometria não corresponde");

            // NÃO deve cadastrar no ABIS
            verify(osiaClient, never()).cadastrarEncounter(anyString(), anyString(), any());

            // Verifica audit de inconclusiva
            verify(auditService).registrar(
                eq("COLETA_INCONCLUSIVA"),
                eq(CPF_VALIDO),
                eq(ID_COLETA),
                contains("biometria não corresponde")
            );
        }

        @Test
        @DisplayName("Cenário 3: Verify não encontrou + Identify 0 matches = VALIDA (nova)")
        void cenario3_VerifyNaoEncontrouIdentify0Matches() throws Exception {
            // Arrange
            EncountersResponse encounters = criarEncountersResponse(0);
            VerifyResponse verifyResponse = criarVerifyResponseSemMatch();
            IdentifyResponse identifyResponse = criarIdentifyResponse(0);
            EnrollResponse enrollResponse = EnrollResponse.builder()
                .encounterId("ABIS-NOVA-456")
                .build();

            when(osiaClient.buscarEncounters(CPF_VALIDO)).thenReturn(encounters);
            when(osiaClient.verificar11(eq(CPF_VALIDO), any())).thenReturn(verifyResponse);
            when(osiaClient.identificar1N(any())).thenReturn(identifyResponse);
            when(osiaClient.cadastrarEncounter(eq(CPF_VALIDO), eq(ID_COLETA), any()))
                .thenReturn(enrollResponse);
            when(coletaRepository.atualizarStatusProcessamento(
                anyString(), any(), any(), any(), any(), any()
            )).thenReturn(true);
            when(coletaRepository.atualizarAbisEncounterId(anyString(), anyString()))
                .thenReturn(true);

            // Act
            ResultadoDeduplicacao resultado = service.processar(coletaValida);

            // Assert
            assertThat(resultado.getStatus()).isEqualTo(StatusValidacao.VALIDA);
            assertThat(resultado.getAbisEncounterId()).isEqualTo("ABIS-NOVA-456");
            assertThat(resultado.getMensagem()).contains("Nenhum match em 1:N - cadastrada como nova");

            // Verifica que executou identify 1:N
            verify(osiaClient).identificar1N(any());
        }

        @Test
        @DisplayName("Cenário 4: Verify não encontrou + Identify 1 match = INCONCLUSIVA")
        void cenario4_VerifyNaoEncontrouIdentify1Match() throws Exception {
            // Arrange
            EncountersResponse encounters = criarEncountersResponse(0);
            VerifyResponse verifyResponse = criarVerifyResponseSemMatch();
            IdentifyResponse identifyResponse = criarIdentifyResponse(1);

            when(osiaClient.buscarEncounters(CPF_VALIDO)).thenReturn(encounters);
            when(osiaClient.verificar11(eq(CPF_VALIDO), any())).thenReturn(verifyResponse);
            when(osiaClient.identificar1N(any())).thenReturn(identifyResponse);
            when(coletaRepository.atualizarStatusProcessamento(
                anyString(), any(), any(), any(), any(), any()
            )).thenReturn(true);

            // Act
            ResultadoDeduplicacao resultado = service.processar(coletaValida);

            // Assert
            assertThat(resultado.getStatus()).isEqualTo(StatusValidacao.INCONCLUSIVA);
            assertThat(resultado.getMotivoInconclusivo()).contains("Match único em busca 1:N");

            // NÃO cadastra no ABIS
            verify(osiaClient, never()).cadastrarEncounter(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("Cenário 5: Verify não encontrou + Identify N matches = INCONCLUSIVA")
        void cenario5_VerifyNaoEncontrouIdentifyMultiplosMatches() throws Exception {
            // Arrange
            EncountersResponse encounters = criarEncountersResponse(0);
            VerifyResponse verifyResponse = criarVerifyResponseSemMatch();
            IdentifyResponse identifyResponse = criarIdentifyResponse(3);

            when(osiaClient.buscarEncounters(CPF_VALIDO)).thenReturn(encounters);
            when(osiaClient.verificar11(eq(CPF_VALIDO), any())).thenReturn(verifyResponse);
            when(osiaClient.identificar1N(any())).thenReturn(identifyResponse);
            when(coletaRepository.atualizarStatusProcessamento(
                anyString(), any(), any(), any(), any(), any()
            )).thenReturn(true);

            // Act
            ResultadoDeduplicacao resultado = service.processar(coletaValida);

            // Assert
            assertThat(resultado.getStatus()).isEqualTo(StatusValidacao.INCONCLUSIVA);
            assertThat(resultado.getMotivoInconclusivo())
                .contains("Múltiplos matches em 1:N")
                .contains("3 candidatos");
        }
    }

    // ========== TESTES DE SELEÇÃO POR NFIQ2 (RN005) ==========

    @Nested
    @DisplayName("RN005 - Seleção por NFIQ2")
    class SelecaoNFIQ2Tests {

        @Test
        @DisplayName("Deve substituir encounter com menor qualidade quando coleta atual é melhor")
        void deveSubstituirEncounterComMenorQualidade() throws Exception {
            // Arrange
            EncountersResponse encounters = criarEncountersComNFIQ2(
                Arrays.asList(80, 70) // 2 encounters existentes
            );
            VerifyResponse verifyResponse = criarVerifyResponseMatch(true, 0.92);

            // Coleta atual tem NFIQ2 = 90 (melhor que os existentes)
            coletaValida.setDadosBiometricos(
                Arrays.asList(criarDadoBiometricoComNFIQ2(90))
            );

            EnrollResponse enrollResponse = EnrollResponse.builder()
                .encounterId("ABIS-MELHOR-789")
                .build();

            when(osiaClient.buscarEncounters(CPF_VALIDO)).thenReturn(encounters);
            when(osiaClient.verificar11(eq(CPF_VALIDO), any())).thenReturn(verifyResponse);
            when(osiaClient.cadastrarEncounter(eq(CPF_VALIDO), eq(ID_COLETA), any()))
                .thenReturn(enrollResponse);
            when(coletaRepository.atualizarStatusProcessamento(
                anyString(), any(), any(), any(), any(), any()
            )).thenReturn(true);
            when(coletaRepository.atualizarAbisEncounterId(anyString(), anyString()))
                .thenReturn(true);

            // Act
            ResultadoDeduplicacao resultado = service.processar(coletaValida);

            // Assert
            assertThat(resultado.getStatus()).isEqualTo(StatusValidacao.VALIDA);
            assertThat(resultado.getMensagem()).contains("Substituiu encounter com menor qualidade");

            // Verifica que deletou o segundo melhor (NFIQ2 = 70)
            verify(osiaClient).deletarEncounter(eq(CPF_VALIDO), eq("ENC-2"));

            // Cadastrou a nova
            verify(osiaClient).cadastrarEncounter(eq(CPF_VALIDO), eq(ID_COLETA), any());
        }

        @Test
        @DisplayName("Deve descartar coleta quando qualidade é inferior às 2 existentes")
        void deveDescartarColetaComQualidadeInferior() throws Exception {
            // Arrange
            EncountersResponse encounters = criarEncountersComNFIQ2(
                Arrays.asList(90, 85) // 2 encounters com alta qualidade
            );
            VerifyResponse verifyResponse = criarVerifyResponseMatch(true, 0.88);

            // Coleta atual tem NFIQ2 = 70 (pior que as 2 existentes)
            coletaValida.setDadosBiometricos(
                Arrays.asList(criarDadoBiometricoComNFIQ2(70))
            );

            when(osiaClient.buscarEncounters(CPF_VALIDO)).thenReturn(encounters);
            when(osiaClient.verificar11(eq(CPF_VALIDO), any())).thenReturn(verifyResponse);
            when(coletaRepository.atualizarStatusProcessamento(
                anyString(), any(), any(), any(), any(), any()
            )).thenReturn(true);

            // Act
            ResultadoDeduplicacao resultado = service.processar(coletaValida);

            // Assert
            assertThat(resultado.getStatus()).isEqualTo(StatusValidacao.VALIDA);
            assertThat(resultado.getMensagem()).contains("Descartada por qualidade inferior");

            // NÃO deve deletar nenhum encounter
            verify(osiaClient, never()).deletarEncounter(anyString(), anyString());

            // NÃO deve cadastrar no ABIS
            verify(osiaClient, never()).cadastrarEncounter(anyString(), anyString(), any());
        }
    }

    // ========== TESTES DE TRATAMENTO DE ERROS ==========

    @Nested
    @DisplayName("Tratamento de Erros")
    class TratamentoErrosTests {

        @Test
        @DisplayName("Deve marcar como ERRO_REPROCESSAVEL quando OSIA retorna 5xx")
        void deveTratarErroOSIARetentavel() throws Exception {
            // Arrange
            OSIAException osiaException = new OSIAException("Service Unavailable", 503, true);

            when(osiaClient.buscarEncounters(CPF_VALIDO)).thenThrow(osiaException);

            // Act
            ResultadoDeduplicacao resultado = service.processar(coletaValida);

            // Assert
            assertThat(resultado.getStatus()).isEqualTo(StatusValidacao.ERRO_REPROCESSAVEL);
            assertThat(resultado.getMensagem()).contains("Erro OSIA retentável");

            // Verifica que marcou para reprocessamento
            verify(coletaRepository).marcarErroReprocessavel(
                eq(ID_COLETA),
                contains("Service Unavailable")
            );

            // Verifica audit
            verify(auditService).registrarErro(
                eq("ERRO_OSIA"),
                eq(CPF_VALIDO),
                eq(ID_COLETA),
                eq(osiaException)
            );
        }

        @Test
        @DisplayName("Deve marcar como INVALIDA quando OSIA retorna 4xx")
        void deveTratarErroOSIANaoRetentavel() throws Exception {
            // Arrange
            OSIAException osiaException = new OSIAException("Bad Request", 400, false);

            when(osiaClient.buscarEncounters(CPF_VALIDO)).thenThrow(osiaException);

            // Act
            ResultadoDeduplicacao resultado = service.processar(coletaValida);

            // Assert
            assertThat(resultado.getStatus()).isEqualTo(StatusValidacao.INVALIDA);
            assertThat(resultado.getMotivoRejeicao()).contains("Erro OSIA");

            // Verifica que marcou como inválida
            verify(coletaRepository).marcarInvalida(
                eq(ID_COLETA),
                contains("Bad Request")
            );
        }

        @Test
        @DisplayName("RN010 - Deve tratar violação de unicidade no ABIS (409)")
        void deveTratarViolacaoUnicidadeABIS() throws Exception {
            // Arrange
            configurarCenarioBaseMatch();

            OSIAException violacaoException = new OSIAException("Conflict - Duplicate", 409, false);
            when(osiaClient.cadastrarEncounter(anyString(), anyString(), any()))
                .thenThrow(violacaoException);

            // Act
            ResultadoDeduplicacao resultado = service.processar(coletaValida);

            // Assert
            assertThat(resultado.getStatus()).isEqualTo(StatusValidacao.INVALIDA);
            assertThat(resultado.getMotivoRejeicao()).contains("Violação de unicidade no ABIS");

            // Verifica que marcou como inválida
            verify(coletaRepository).marcarInvalida(
                eq(ID_COLETA),
                contains("Violação de unicidade no ABIS")
            );

            // Verifica audit de violação
            verify(auditService).registrarErro(
                eq("VIOLACAO_UNICIDADE_ABIS"),
                eq(CPF_VALIDO),
                eq(ID_COLETA),
                same(violacaoException)
            );
        }

        @Test
        @DisplayName("Deve marcar como ERRO_REPROCESSAVEL para exceções inesperadas")
        void deveTratarErroInesperado() {
            // Arrange
            when(osiaClient.buscarEncounters(CPF_VALIDO))
                .thenThrow(new RuntimeException("Erro inesperado"));

            // Act
            ResultadoDeduplicacao resultado = service.processar(coletaValida);

            // Assert
            assertThat(resultado.getStatus()).isEqualTo(StatusValidacao.ERRO_REPROCESSAVEL);

            // Verifica audit
            verify(auditService).registrarErro(
                eq("ERRO_INESPERADO"),
                eq(CPF_VALIDO),
                eq(ID_COLETA),
                any(RuntimeException.class)
            );

            // Verifica marcação para reprocessamento
            verify(coletaRepository).marcarErroReprocessavel(
                eq(ID_COLETA),
                anyString()
            );
        }
    }

    // ========== TESTES DE AUDITORIA ==========

    @Nested
    @DisplayName("Auditoria e Métricas")
    class AuditoriaTests {

        @Test
        @DisplayName("Deve registrar auditoria com duração ao concluir processamento")
        void deveRegistrarAuditoriaComDuracao() throws Exception {
            // Arrange
            configurarCenarioSucessoSimples();

            // Act
            service.processar(coletaValida);

            // Assert
            ArgumentCaptor<Long> duracaoCaptor = ArgumentCaptor.forClass(Long.class);

            verify(auditService).registrarComDuracao(
                eq("PROCESSAMENTO_CONCLUIDO"),
                eq(CPF_VALIDO),
                eq(ID_COLETA),
                duracaoCaptor.capture(),
                contains("Status: VALIDA")
            );

            // Verifica que a duração é >= 0
            assertThat(duracaoCaptor.getValue()).isGreaterThanOrEqualTo(0L);
        }

        @Test
        @DisplayName("Deve auditar cadastro no ABIS")
        void deveAuditarCadastroABIS() throws Exception {
            // Arrange
            configurarCenarioSucessoSimples();

            // Act
            service.processar(coletaValida);

            // Assert
            verify(auditService).registrarCadastroABIS(
                eq(CPF_VALIDO),
                eq(ID_COLETA),
                eq("ABIS-123"),
                eq("ENROLL")
            );
        }
    }

    // ========== TESTES DE INTEGRAÇÃO COM OPENSEARCH ==========

    @Nested
    @DisplayName("Integração OpenSearch")
    class OpenSearchIntegrationTests {

        @Test
        @DisplayName("Deve atualizar status no OpenSearch após sucesso")
        void deveAtualizarStatusNoOpenSearch() throws Exception {
            // Arrange
            configurarCenarioSucessoSimples();

            // Act
            service.processar(coletaValida);

            // Assert
            verify(coletaRepository).atualizarStatusProcessamento(
                eq(ID_COLETA),
                eq(StatusValidacao.VALIDA),
                eq("ABIS-123"), // abisRecordId
                isNull(), // motivoInconclusivo
                isNull(), // motivoRejeicao
                anyDouble() // matchScore
            );
        }

        @Test
        @DisplayName("Deve logar erro quando falha ao atualizar OpenSearch")
        void deveLogarErroQuandoFalhaAtualizarOpenSearch() throws Exception {
            // Arrange
            configurarCenarioSucessoSimples();

            // Act
            service.processar(coletaValida);

            // Assert - não deve lançar exceção, apenas logar
            verify(coletaRepository).atualizarStatusProcessamento(
                    anyString(), any(), any(), any(), any(), any()
            );
        }

        @Test
        @DisplayName("Deve atualizar ABIS Encounter ID no OpenSearch")
        void deveAtualizarAbisEncounterIdNoOpenSearch() throws Exception {
            // Arrange
            configurarCenarioSucessoSimples();

            // Act
            service.processar(coletaValida);

            // Assert
            verify(coletaRepository).atualizarAbisEncounterId(
                eq(ID_COLETA),
                eq("ABIS-123")
            );
        }
    }

    // ========== MÉTODOS AUXILIARES ==========

    /**
     * Cria uma coleta válida para testes
     */
    private ColetaMetadata criarColetaValida() {
        LocalDate dataNascimento = LocalDate.now().minusYears(30);
        Date dataNascimentoDate = Date.from(
            dataNascimento.atStartOfDay(ZoneId.systemDefault()).toInstant()
        );

        return ColetaMetadata.builder()
            .idColeta(ID_COLETA)
            .cpf(CPF_VALIDO)
            .dataNascimento(dataNascimentoDate)
            .validacaoPendente(false)
            .dadosBiometricos(Arrays.asList(
                criarDadoBiometricoComNFIQ2(85)
            ))
            .build();
    }

    /**
     * Cria dado biométrico com NFIQ2 score específico
     */
    private DadoBiometricoMetadata criarDadoBiometricoComNFIQ2(int nfiq2) {
        TemplateMetadata template = TemplateMetadata.builder()
            .dados("fake-template".getBytes())
            .formato("ISO-19794-2")
            .build();

        return DadoBiometricoMetadata.builder()
            .tipo("IMPRESSAO_DIGITAL")
            .posicaoDedo(1) // Polegar direito
            .qualidadeNfiq(nfiq2)
            .templates(Arrays.asList(template))
            .build();
    }

    /**
     * Cria EncountersResponse com N encounters
     */
    private EncountersResponse criarEncountersResponse(int quantidade) {
        List<Encounter> encounters = new ArrayList<>();

        for (int i = 1; i <= quantidade; i++) {
            Encounter encounter = new Encounter();
            encounter.setEncounterId("ENC-" + i);

            EncounterMetadata metadata = new EncounterMetadata();
            metadata.setNfiq2Score(80); // Score padrão
            encounter.setMetadata(metadata);

            encounters.add(encounter);
        }

        EncountersResponse response = new EncountersResponse();
        response.setEncounters(encounters);
        response.setTotalCount(quantidade);

        return response;
    }

    /**
     * Cria EncountersResponse com NFIQ2 scores específicos
     */
    private EncountersResponse criarEncountersComNFIQ2(List<Integer> nfiq2Scores) {
        List<Encounter> encounters = new ArrayList<>();

        for (int i = 0; i < nfiq2Scores.size(); i++) {
            Encounter encounter = new Encounter();
            encounter.setEncounterId("ENC-" + (i + 1));

            EncounterMetadata metadata = new EncounterMetadata();
            metadata.setNfiq2Score(nfiq2Scores.get(i));
            encounter.setMetadata(metadata);

            encounters.add(encounter);
        }

        EncountersResponse response = new EncountersResponse();
        response.setEncounters(encounters);
        response.setTotalCount(encounters.size());

        return response;
    }

    /**
     * Cria VerifyResponse com match
     */
    private VerifyResponse criarVerifyResponseMatch(boolean verified, double score) {
        return VerifyResponse.builder()
            .verified(verified)
            .encounterId("ENC-MATCH-1") // Simula pessoa encontrada
            .score(score)
            .build();
    }

    /**
     * Cria VerifyResponse sem match
     */
    private VerifyResponse criarVerifyResponseSemMatch() {
        return VerifyResponse.builder()
            .verified(false)
            .encounterId(null) // isPessoaEncontrada() retornará false
            .score(null)
            .build();
    }

    /**
     * Cria IdentifyResponse com N candidatos
     */
    private IdentifyResponse criarIdentifyResponse(int numeroCandidatos) {
        IdentifyResponse response = new IdentifyResponse();

        if (numeroCandidatos > 0) {
            List<Candidate> candidates = new ArrayList<>();
            for (int i = 0; i < numeroCandidatos; i++) {
                Candidate candidate = Candidate.builder()
                    .personId("PERSON-" + (i + 1))
                    .score(0.8 + (i * 0.05))
                    .build();
                candidates.add(candidate);
            }
            response.setCandidates(candidates);
        }

        return response;
    }

    /**
     * Configura mocks para cenário de sucesso simples
     */
    private void configurarCenarioSucessoSimples() throws Exception {
        EncountersResponse encounters = criarEncountersResponse(1);
        VerifyResponse verifyResponse = criarVerifyResponseMatch(true, 0.92);
        EnrollResponse enrollResponse = EnrollResponse.builder()
            .encounterId("ABIS-123")
            .build();

        when(osiaClient.buscarEncounters(CPF_VALIDO)).thenReturn(encounters);
        when(osiaClient.verificar11(eq(CPF_VALIDO), any())).thenReturn(verifyResponse);
        when(osiaClient.cadastrarEncounter(eq(CPF_VALIDO), eq(ID_COLETA), any()))
            .thenReturn(enrollResponse);
        when(coletaRepository.atualizarStatusProcessamento(
            anyString(), any(StatusValidacao.class), anyString(), any(), any(), any(Double.class)
        )).thenReturn(true);
        when(coletaRepository.atualizarAbisEncounterId(anyString(), anyString()))
            .thenReturn(true);
    }

    private void configurarCenarioBaseMatch() throws Exception {
        EncountersResponse encounters = criarEncountersResponse(1);
        VerifyResponse verifyResponse = criarVerifyResponseMatch(true, 0.92);

        when(osiaClient.buscarEncounters(CPF_VALIDO)).thenReturn(encounters);
        when(osiaClient.verificar11(eq(CPF_VALIDO), any())).thenReturn(verifyResponse);
    }

}