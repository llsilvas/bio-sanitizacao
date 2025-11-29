

package bio.prodesp.deduplicacao.commons.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

@Getter
@Setter
@Builder
@JsonInclude( JsonInclude.Include.NON_NULL )
@JsonIgnoreProperties(ignoreUnknown = true)
public class DadosColetaMetadata implements Serializable {

    @Serial
    private static final long serialVersionUID = 1738880644926939276L;

    private Date data;

    private String idEstacao;

    private String codigoLocal;
    
    private String nomeLocal;
    
    private String cpfOperador;
    
    private String nomeOperador;

    private String tipoEstacao;
    
    private String ipEstacao;
    
    private String codigoEstacao;

    private String cpf;
    private String rg;
    private LocalDateTime dataColeta;
    private String tipoColeta;
    
    /**
     * Id externo da coleta (ex. ID da coleta na Valid)
     */
    private String idExterno;
    
    /**
     * Data de envio da coleta ao ABIS (Valid)
     */
    private Date dataEnvioAbis;
       
    private Date dataEnvioDenatran;

    /**
     * Nome do host que efetuou a coleta
     */
    private String hostname;

    private SoftwareCapturaMetadata softwareCaptura;
    private Integer posicaoDedo;

    private byte[] imagem;


}
