package bio.prodesp.deduplicacao.commons.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;

@JsonInclude( JsonInclude.Include.NON_NULL )
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmissaoDocumentoMetadata {

    //Indica o tipo de documento que foi emitido/impresso. [RG/CNH]
    private String tipoDocumento;

    //Identifica o número do formulário utilizado para impressão (papel moeda controlado).
    private String espelho;

    //Identifica o número do RENACH que foi emitido, no caso de CNH.
    private String renach;

    // Identifica o número do protocolo do IIRGD que foi emitido, no caso de RG.
    private String protocolo;

    //A data da emissão do documento, conforme impresso no documento.
    private Date dataEmissao;

    public String getTipoDocumento() {
        return tipoDocumento;
    }

    public void setTipoDocumento(String tipoDocumento) {
        this.tipoDocumento = tipoDocumento;
    }

    public String getEspelho() {
        return espelho;
    }

    public void setEspelho(String espelho) {
        this.espelho = espelho;
    }

    public String getRenach() {
        return renach;
    }

    public void setRenach(String renach) {
        this.renach = renach;
    }

    public String getProtocolo() {
        return protocolo;
    }

    public void setProtocolo( String protocolo ) {
        this.protocolo = protocolo;
    }

    public Date getDataEmissao() {
        return dataEmissao;
    }

    public void setDataEmissao(Date dataEmissao) {
        this.dataEmissao = dataEmissao;
    }
}
