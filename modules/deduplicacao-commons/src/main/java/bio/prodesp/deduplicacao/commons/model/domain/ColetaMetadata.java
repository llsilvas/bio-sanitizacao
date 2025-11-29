

package bio.prodesp.deduplicacao.commons.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

@Builder
@JsonInclude( JsonInclude.Include.NON_NULL )
@JsonIgnoreProperties(ignoreUnknown = true)
public class ColetaMetadata implements Serializable {

    @Serial
    private static final long serialVersionUID = -8896820618402015551L;

    private String idColeta;
    
    private String idPessoa;
    
    private String idAtendimento;
    
    private String finalidade;
    
    private String localGravacao;
    
    private String motivo;
    
    private String rgSp;
    
    private String rgSpFormatado;
    
    private String cpf;
    
    private Date dataNascimento;
    
    private Boolean validacaoPendente;
    
    private Boolean valida;
    
    private Boolean recadastroRgSp;

    private Boolean importacao;

    private String documentoResponsavel;
    
    private String ufDocumentoResponsavel;
        
    private String observacoesAnomalias;
    
    private ProblemaMetadata[] problemasValidacao;
    
    private Collection<DadoBiometricoMetadata> dadosBiometricos;

    private DadosColetaMetadata dadosColeta;
    
    private Date dataInclusao;
    
    private String clientInclusao;
    
    private Date dataAtualizacao;
    
    private String clientAtualizacao;
    
    private FicMetadata fic;


    /**
     * Nome completo do cidadão
     */
    private String nomeCompleto;

    /**
     * Nome social do cidadão. Opcional.
     */
    private String nomeSocial;

    /**
     * Código que identifica o local onde a coleta deverá ser realizada. Deverá ser utilizado o código que
     * identifica o posto do Poupatempo, delegacia ou Ciretrans
     */
    private String codigoLocal;

    /**
     * Senha exibida no painel de atendimento (fila), emitida quando o cidadão chega no posto, e utilizada
     * para chamar o cidadão para o atendimento na mesa de coleta. Esta senha será utilizada para que a
     * estação de coleta consiga identificar o cidadão que está sendo recebido para coleta.
     */
    private String senhaAtendimento;

    // issue#11
    private Boolean excluido;
    private String motivoExclusao;
    private Date   dataExclusao;
    // issue#11

    /**
     * BreadcrumbId da gravação da coleta
     */
    private String breadcrumbId; //issue biometria#44

    /**
     * Documentos emitidos com a coleta
     */
    private List<EmissaoDocumentoMetadata> emissoes;

    private String renach;

    private String status;

    private String processo;

    private Date dataDeduplicacao;

    private String idColetaValid;

    private Integer posicaoDedo;

    public Integer getPosicaoDedo() {
        return posicaoDedo;
    }

    public void setPosicaoDedo(Integer posicaoDedo) {
        this.posicaoDedo = posicaoDedo;
    }

    /**
     * Resultado do confronto de digital realizado na mesa de atendimento antes do envio da coleta
     */
    private ConfrontoColetaMetadata confronto;
    private String idAvaliacao;
    private Collection<RenachMetadata> renachs;

    @Override
    public String toString( ) {
        return "[" +
            this.getCodigoLocal() + "," +
            this.getCpf()+ "," +
            this.getFinalidade()+ "," +
            this.getIdAtendimento()+ "," +
            this.getIdColeta()+ "," +
            this.getLocalGravacao()+ "," +
            this.getMotivo()+ "," +
            this.getNomeCompleto()+ "," +
            this.getNomeSocial()+ "," +
            this.getRgSp()+ "," +
            this.getSenhaAtendimento()+ "," +
            "]";
    }
    
    public String getIdColeta() {
        return idColeta;
    }

    public void setIdColeta( String idColeta) {
        this.idColeta = idColeta;
    }

    public String getIdPessoa() {
        return idPessoa;
    }

    public void setIdPessoa(String idPessoa) {
        this.idPessoa = idPessoa;
    }

    public String getIdAtendimento() {
        return idAtendimento;
    }

    public void setIdAtendimento(String idAtendimento) {
        this.idAtendimento = idAtendimento;
    }

    public String getFinalidade() {
        return finalidade;
    }

    public void setFinalidade(String finalidade) {
        this.finalidade = finalidade;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public String getRgSp() {
        return rgSp;
    }

    public void setRgSp(String rgSp) {
        this.rgSp = rgSp;
    }

    public String getRgSpFormatado() {
        return rgSpFormatado;
    }

    public void setRgSpFormatado( String rgSpFormatado ) {
        this.rgSpFormatado = rgSpFormatado;
    }
    
    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public Date getDataNascimento() {
        return dataNascimento;
    }

    public void setDataNascimento(Date dataNascimento) {
        this.dataNascimento = dataNascimento;
    }

    public Boolean getValidacaoPendente() {
        return validacaoPendente;
    }

    public void setValidacaoPendente(Boolean validacaoPendente) {
        this.validacaoPendente = validacaoPendente;
    }

    public Boolean getRecadastroRgSp() {
        return recadastroRgSp;
    }

    public void setRecadastroRgSp( Boolean recadastroRgSp ) {
        this.recadastroRgSp = recadastroRgSp;
    }

    public String getDocumentoResponsavel() {
        return documentoResponsavel;
    }

    public void setDocumentoResponsavel( String documentoResponsavel ) {
        this.documentoResponsavel = documentoResponsavel;
    }

    public String getUfDocumentoResponsavel() {
        return ufDocumentoResponsavel;
    }

    public void setUfDocumentoResponsavel( String ufDocumentoResponsavel ) {
        this.ufDocumentoResponsavel = ufDocumentoResponsavel;
    }

    public Collection<DadoBiometricoMetadata> getDadosBiometricos() {
        return dadosBiometricos;
    }

    public void setDadosBiometricos(Collection<DadoBiometricoMetadata> dadosBiometricos) {
        this.dadosBiometricos = dadosBiometricos;
    }

    public DadosColetaMetadata getDadosColeta() {
        return dadosColeta;
    }

    public void setDadosColeta(DadosColetaMetadata dadosColeta) {
        this.dadosColeta = dadosColeta;
    }

    public String getLocalGravacao() {
        return localGravacao;
    }

    public void setLocalGravacao(String localGravacao) {
        this.localGravacao = localGravacao;
    }

    public Date getDataInclusao() {
        return dataInclusao;
    }

    public void setDataInclusao( Date dataInclusao ) {
        this.dataInclusao = dataInclusao;
    }

    public String getClientInclusao() {
        return clientInclusao;
    }

    public void setClientInclusao( String clientInclusao ) {
        this.clientInclusao = clientInclusao;
    }

    public Date getDataAtualizacao() {
        return dataAtualizacao;
    }

    public void setDataAtualizacao( Date dataAtualizacao ) {
        this.dataAtualizacao = dataAtualizacao;
    }

    public String getClientAtualizacao() {
        return clientAtualizacao;
    }

    public void setClientAtualizacao( String clientAtualizacao ) {
        this.clientAtualizacao = clientAtualizacao;
    }

    public String getNomeCompleto() {
        return nomeCompleto;
    }

    public void setNomeCompleto( String nomeCompleto ) {
        this.nomeCompleto = nomeCompleto;
    }

    public String getNomeSocial() {
        return nomeSocial;
    }

    public void setNomeSocial( String nomeSocial ) {
        this.nomeSocial = nomeSocial;
    }

    public String getCodigoLocal() {
        return codigoLocal;
    }

    public void setCodigoLocal( String codigoLocal ) {
        this.codigoLocal = codigoLocal;
    }

    public String getSenhaAtendimento() {
        return senhaAtendimento;
    }

    public void setSenhaAtendimento( String senhaAtendimento ) {
        this.senhaAtendimento = senhaAtendimento;
    }

    public Boolean getValida() {
        return valida;
    }

    public void setValida( Boolean valida ) {
        this.valida = valida;
    }

    public ProblemaMetadata[] getProblemasValidacao() {
        return problemasValidacao;
    }

    public void setProblemasValidacao( ProblemaMetadata[] problemasValidacao ) {
        this.problemasValidacao = problemasValidacao;
    }
    /**
     * @return the excluido
     */
    public Boolean getExcluido() {
        return excluido;
    }

    /**
     * @param excluido the excluido to set
     */
    public void setExcluido( Boolean excluido ) {
        this.excluido = excluido;
    }

    /**
     * @return the movitoExclusao
     */
    public String getMotivoExclusao() {
        return motivoExclusao;
    }

    /**
     * @param movitoExclusao the movitoExclusao to set
     */
    public void setMotivoExclusao( String movitoExclusao ) {
        this.motivoExclusao = movitoExclusao;
    }

    /**
     * @return the dataExclusao
     */
    public Date getDataExclusao() {
        return dataExclusao;
    }

    /**
     * @param dataExclusao the dataExclusao to set
     */
    public void setDataExclusao( Date dataExclusao ) {
        this.dataExclusao = dataExclusao;
    }

    public String getObservacoesAnomalias() {
        return observacoesAnomalias;
    }

    public void setObservacoesAnomalias( String observacoesAnomalias ) {
        this.observacoesAnomalias = observacoesAnomalias;
    }

    public String getBreadcrumbId() {
        return breadcrumbId;
    }

    public void setBreadcrumbId( String breadcrumbId ) {
        this.breadcrumbId = breadcrumbId;
    }

    public FicMetadata getFic() {
        return fic;
    }

    public void setFic( FicMetadata fic ) {
        this.fic = fic;
    }

    public List<EmissaoDocumentoMetadata> getEmissoes() { return emissoes; }

    public void setEmissoes(List<EmissaoDocumentoMetadata> emissoes) { this.emissoes = emissoes; }

    public void setStatus( String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public String getRenach() {
        return renach;
    }

    public void setRenach( String renach ) {
        this.renach = renach;
    }

    public String getProcesso() {
        return processo;
    }

    public void setProcesso( String processo ) {
        this.processo = processo;
    }

    public ConfrontoColetaMetadata getConfronto() {
        return confronto;
    }

    public void setConfronto( ConfrontoColetaMetadata confronto ) {
        this.confronto = confronto;
    }

    public Date getDataDeduplicacao() {
        return dataDeduplicacao;
    }

    public void setDataDeduplicacao( Date dataDeduplicacao ) {
        this.dataDeduplicacao = dataDeduplicacao;
    }

    public void setIdAvaliacao( String idAvaliacao ) {
        this.idAvaliacao = idAvaliacao;
    }

    public String getIdAvaliacao() {
       return this.idAvaliacao;
    }

    public Collection<RenachMetadata> getRenachs() {
        return renachs;
    }

    public void setRenachs( Collection<RenachMetadata> renachs ) {
        this.renachs = renachs;
    }

    public void setImportacao( final Boolean importacao ) {
        this.importacao = importacao;
    }

    public Boolean getImportacao() {
        return this.importacao;
    }

    public String getIdColetaValid() {
        return idColetaValid;
    }

    public void setIdColetaValid(String idColetaValid) {
        this.idColetaValid = idColetaValid;
    }
}
