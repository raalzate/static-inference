/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package banco_hexagonal.pruebatecnica.domain.Exception;

/**
 *
 * @author AustinSalguero
 */
public class EntidadNoEncontradaException extends RuntimeException{
    public EntidadNoEncontradaException( String message){
        super(message);
    }
}
