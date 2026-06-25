package duoc.sumativa.transportes.controller;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import duoc.sumativa.transportes.model.GuiaDespacho;
import duoc.sumativa.transportes.model.Pedido;
import duoc.sumativa.transportes.repository.GuiaRepository;
import duoc.sumativa.transportes.repository.PedidoRepository;
import duoc.sumativa.transportes.service.GuiaService;

@RestController
@RequestMapping("/api/transportes")
public class GuiaController {

    private final GuiaService guiaService;
    private final PedidoRepository pedidoRepository;
    private final GuiaRepository guiaRepository;

    public GuiaController(GuiaService guiaService, PedidoRepository pedidoRepository, GuiaRepository guiaRepository) {
        this.guiaService = guiaService;
        this.pedidoRepository = pedidoRepository;
        this.guiaRepository = guiaRepository;
    }

    // ENDPOINTS DE PEDIDOS
    @PostMapping("/pedidos")
    public ResponseEntity<Pedido> crearPedido(@RequestBody Pedido nuevoPedido) {
        nuevoPedido.setFechaPedido(LocalDate.now());
        nuevoPedido.setCodigoPedido("PED-" + System.currentTimeMillis());
        Pedido guardado = pedidoRepository.save(nuevoPedido);
        return ResponseEntity.status(HttpStatus.CREATED).body(guardado);
    }

   
    // ENDPOINTS DE GUÍAS DE DESPACHO
    // ===================================================================
    @PostMapping("/guias/subir")
    public ResponseEntity<String> crearYSubirGuia(
            @RequestParam("codigoPedido") String codigoPedido,
            @RequestParam("transportista") String transportista,
            @RequestParam("archivo") MultipartFile archivo) {
        try {
            if (archivo.isEmpty()) {
                return ResponseEntity.badRequest().body("El archivo PDF esta vacio.");
            }
            String rutaS3 = guiaService.registrarYSubirGuia(codigoPedido, transportista, archivo);
            return ResponseEntity.status(HttpStatus.CREATED).body("Guia procesada y subida a S3: " + rutaS3);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error de almacenamiento: " + e.getMessage());
        }
    }

    @GetMapping("/guias/descargar")
    public ResponseEntity<?> descargarGuia(
            @RequestParam("rutaS3") String rutaS3,
            @RequestHeader(value = "Authorization", required = false) String token) {
        
        if (token == null || !token.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Acceso denegado: Token invalido.");
        }

        try {
            InputStream flujo = guiaService.descargarGuia(rutaS3);
            Resource recurso = new InputStreamResource(flujo);
            String nombreArchivo = rutaS3.substring(rutaS3.lastIndexOf("/") + 1);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreArchivo + "\"")
                    .body(recurso);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Archivo no encontrado en S3: " + e.getMessage());
        }
    }

    @PutMapping("/guias/actualizar")
    public ResponseEntity<String> actualizarGuia(
            @RequestParam("codigoPedido") String codigoPedido,
            @RequestParam("transportista") String transportista,
            @RequestParam("rutaS3Antigua") String rutaS3Antigua,
            @RequestParam("archivoNuevo") MultipartFile archivoNuevo) {
        try {
            guiaService.eliminarGuia(rutaS3Antigua);
            String nuevaRuta = guiaService.registrarYSubirGuia(codigoPedido, transportista, archivoNuevo);
            return ResponseEntity.ok("Guia actualizada con éxito. Nueva ruta: " + nuevaRuta);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al actualizar archivo: " + e.getMessage());
        }
    }

    @DeleteMapping("/guias/eliminar")
    public ResponseEntity<String> eliminarGuia(@RequestParam("rutaS3") String rutaS3) {
        try {
            guiaService.eliminarGuia(rutaS3);
            return ResponseEntity.ok("Archivo eliminado de AWS S3 con exito.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al eliminar: " + e.getMessage());
        }
    }

    @GetMapping("/guias/buscar")
    public ResponseEntity<List<GuiaDespacho>> consultarGuias(
            @RequestParam("transportista") String transportista,
            @RequestParam("fecha") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        
        // Ejecutamos la búsqueda usando el nuevo método del repositorio (usando LocalDate directamente)
        List<GuiaDespacho> resultados = guiaRepository.findByTransportistaAndFechaEmision(transportista, fecha);
        return ResponseEntity.ok(resultados);
    }

     @GetMapping("/guias/listar")
    public ResponseEntity<?> listarArchivosS3() {
        try {
            List<String> archivos = guiaService.listarArchivos();
            return ResponseEntity.ok(archivos);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al listar archivos: " + e.getMessage());
        }
    }
}