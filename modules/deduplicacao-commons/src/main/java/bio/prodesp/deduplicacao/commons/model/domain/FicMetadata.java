
package bio.prodesp.deduplicacao.commons.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;
import java.util.UUID;


@JsonInclude( JsonInclude.Include.NON_NULL )
@JsonIgnoreProperties(ignoreUnknown = true)
public class FicMetadata {

    private String id;
    
    private boolean gerada;
    
    private Date data;
    
    private String retorno;
    
    private String breadcrumbId;
    
    private String rgSp;
    
    private String idColeta;

    private Long duracao;
    
    private String exception;

    public FicMetadata() {
        id = UUID.randomUUID().toString();
        data = new Date();
    }

    public FicMetadata( boolean gerada, String retorno, String breadcrumbId, String rgSp, String idColeta ) {
        this();
        this.gerada = gerada;
        this.retorno = retorno;
        this.breadcrumbId = breadcrumbId;
        this.rgSp = rgSp;
        this.idColeta = idColeta;
    }
        
    public String getId() {
        return id;
    }

    public void setId( String id ) {
        this.id = id;
    }

    public boolean isGerada() {
        return gerada;
    }

    public void setGerada( boolean gerada ) {
        this.gerada = gerada;
    }

    public Date getData() {
        return data;
    }

    public void setData( Date data ) {
        this.data = data;
    }

    public String getRetorno() {
        return retorno;
    }

    public void setRetorno( String retorno ) {
        this.retorno = retorno;
    }

    public String getBreadcrumbId() {
        return breadcrumbId;
    }

    public void setBreadcrumbId( String breadcrumbId ) {
        this.breadcrumbId = breadcrumbId;
    }

    public String getRgSp() {
        return rgSp;
    }

    public void setRgSp( String rgSp ) {
        this.rgSp = rgSp;
    }

    public String getIdColeta() {
        return idColeta;
    }

    public void setIdColeta( String idColeta ) {
        this.idColeta = idColeta;
    }

    public Long getDuracao() {
        return duracao;
    }

    public void setDuracao(Long duracao) {
        this.duracao = duracao;
    }

    public String getException() {
        return exception;
    }

    public void setException( String exception ) {
        this.exception = exception;
    }

}
