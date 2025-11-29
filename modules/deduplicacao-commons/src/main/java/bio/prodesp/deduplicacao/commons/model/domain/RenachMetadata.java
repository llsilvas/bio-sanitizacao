package bio.prodesp.deduplicacao.commons.model.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.util.Date;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RenachMetadata implements Serializable {


    private Date data;
    private String renach;

    public RenachMetadata( Date data, String renach ) {
        this.data = data;
        this.renach = renach;
    }

    public Date getData() {
        return data;
    }

    public void setData( Date data ) {
        this.data = data;
    }

    public String getRenach() {
        return renach;
    }

    public void setRenach( String renach ) {
        this.renach = renach;
    }

    @Override
    public String toString() {
        return "RenachMetadata{" +
                "data=" + data +
                ", renach='" + renach + '\'' +
                '}';
    }
}
