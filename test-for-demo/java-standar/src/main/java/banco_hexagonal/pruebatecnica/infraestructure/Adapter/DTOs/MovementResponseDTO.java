/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package banco_hexagonal.pruebatecnica.infraestructure.Adapter.DTOs;

import java.time.LocalDate;
import lombok.*;

/**
 *
 * @author AustinSalguero
 */
@Data
public class MovementResponseDTO {
    private Long movementId;
    private LocalDate date;
    private String movementType;
    private Double value;
    private Double balance;
    private AccountResponseDTO account;
}
