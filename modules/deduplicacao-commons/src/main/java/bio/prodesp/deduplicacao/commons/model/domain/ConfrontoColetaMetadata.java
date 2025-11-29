package bio.prodesp.deduplicacao.commons.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude( JsonInclude.Include.NON_NULL )
@JsonIgnoreProperties( ignoreUnknown = true )
public class ConfrontoColetaMetadata {

    private Boolean resultado;
    private Boolean pessoaEncontrada;
    private Integer posicaoDedo;

    public Boolean getResultado() {
        return resultado;
    }

    public void setResultado( Boolean resultado ) {
        this.resultado = resultado;
    }

    public Boolean getPessoaEncontrada() {
        return pessoaEncontrada;
    }

    public void setPessoaEncontrada( Boolean pessoaEncontrada ) {
        this.pessoaEncontrada = pessoaEncontrada;
    }

    public Integer getPosicaoDedo() {
        return posicaoDedo;
    }

    public void setPosicaoDedo( Integer posicaoDedo ) {
        this.posicaoDedo = posicaoDedo;
    }

    public String toString() {
        return "{confronto > resultado: %s, pessoaEncontrada: %s, posicaoDedo: %s }".formatted(
                resultado, pessoaEncontrada, posicaoDedo );
    }
}
