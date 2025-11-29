

package bio.prodesp.deduplicacao.commons.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

@Builder
@Getter
@Setter
@JsonInclude( JsonInclude.Include.NON_NULL )
@JsonIgnoreProperties(ignoreUnknown = true)
public class DadoBiometricoMetadata implements Serializable {

    @Serial
    private static final long serialVersionUID = 7403809908668905163L;

    private String id;
    
    private String tipo;
    
    private Integer tipoDigital;
    
    private Integer posicaoDedo;
    
    private Integer qualidadeNfiq;
    
    private Integer tamanhoImagem;
    
    private String anomalia;
    
    private String anomaliaAssinatura;
    
    private EquipamentoMetadata equipamento;

    private List<TemplateMetadata> templates;
    
    private Boolean responsavel;


    /**
     Indica se a digital coletada pertence a dedo vivo
     - 0: dedo não vivo
     - 1: dedo vivo
     - 2: não foi possível confirmar
     */
    private Integer liveness;

    /**
     * Resultado do confronto entre a digital pousada contra a digital rolada
     */
    private Boolean confronto;

}
