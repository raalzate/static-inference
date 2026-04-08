/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package banco_hexagonal.pruebatecnica.config;

import banco_hexagonal.pruebatecnica.domain.Exception.EntidadNoEncontradaException;
import banco_hexagonal.pruebatecnica.domain.Exception.SaldoInsuficienteException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 *
 * @author Trabajo
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(SaldoInsuficienteException.class)
    public ResponseEntity<String> handleSaldoInsuficiente(SaldoInsuficienteException e){
        return ResponseEntity.badRequest().body(e.getMessage());
    }
    
    @ExceptionHandler(EntidadNoEncontradaException.class)
    public ResponseEntity<String> handleEntidadNoEncontrada(EntidadNoEncontradaException e){
        return ResponseEntity.badRequest().body(e.getMessage());
    }
    
     @ExceptionHandler(DataIntegrityViolationException.class)  
  public ResponseEntity<String> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String errorMessage = "Error de integridad de datos. " +
                              "Asegúrate de que los datos proporcionados sean únicos y válidos. ";
        
        if (ex.getCause() != null && ex.getCause().getMessage().contains("llave duplicada")) {
            errorMessage += "Ya existe un registro con la llave proporcionada.";
        }       
        return new ResponseEntity<>(errorMessage, HttpStatus.CONFLICT);
    }
}
