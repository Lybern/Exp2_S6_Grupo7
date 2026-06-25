package duoc.sumativa.transportes.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "TST_GUIAS_DESPACHO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuiaDespacho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_GUIA")
    private Long id;

    @Column(name = "NUMERO_GUIA", nullable = false, unique = true)
    private String numeroGuia;

    @Column(name = "TRANSPORTISTA", nullable = false)
    private String transportista;

    @Column(name = "FECHA_EMISION", nullable = false)
    private LocalDate fechaEmision;

    @Column(name = "ESTADO", nullable = false)
    private String estado;

    @Column(name = "RUTA_S3", nullable = false, length = 500)
    private String rutaS3;
}