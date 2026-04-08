/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package banco_hexagonal.pruebatecnica.infraestructure.Adapter.DTOs;

import lombok.Data;

/**
 *
 * @author Trabajo
 */
@Data
public class AccountResponseDTO {
    private Long accountNumber;
    private String accountType;
    private Double balance;
    private ClientResponseDTO client;
}
