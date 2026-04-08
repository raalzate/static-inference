/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package banco_hexagonal.pruebatecnica.domain.Exception;

/**
 *
 * @author Trabajo
 */
public class SaldoInsuficienteException extends RuntimeException{
    public SaldoInsuficienteException(String message){
        super(message);
    }
}
