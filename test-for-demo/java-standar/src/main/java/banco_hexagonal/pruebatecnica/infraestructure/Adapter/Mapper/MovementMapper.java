/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package banco_hexagonal.pruebatecnica.infraestructure.Adapter.Mapper;

import banco_hexagonal.pruebatecnica.domain.Model.Movement;
import banco_hexagonal.pruebatecnica.infraestructure.Adapter.DTOs.MovementResponseDTO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 *
 * @author AustinSalguero
 */
@Mapper(componentModel = "spring")
public interface MovementMapper {
    @Mapping(source = "account.accountNumber", target = "account.accountNumber")
    @Mapping(source = "account.initialBalance", target = "account.balance")
    @Mapping(source = "account.client.name", target = "account.client.name")
    MovementResponseDTO toDto(Movement movement);
    List<MovementResponseDTO> toDtoList(List<Movement> movements);
}
