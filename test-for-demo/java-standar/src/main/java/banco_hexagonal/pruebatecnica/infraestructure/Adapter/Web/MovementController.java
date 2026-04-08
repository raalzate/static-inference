
package banco_hexagonal.pruebatecnica.infraestructure.Adapter.Web;

import banco_hexagonal.pruebatecnica.application.Port.In.MovementUseCase;
import banco_hexagonal.pruebatecnica.domain.Model.Movement;
import banco_hexagonal.pruebatecnica.infraestructure.Adapter.DTOs.MovementResponseDTO;
import banco_hexagonal.pruebatecnica.infraestructure.Adapter.Mapper.MovementMapper;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author AustinSalguero
 */
@RestController
@RequestMapping("/movimientos")
public class MovementController {
    private final MovementUseCase movementUseCase;
    private final MovementMapper movementMapper;
    
    public MovementController(MovementUseCase movementUseCase, MovementMapper movementMapper){
        this.movementUseCase = movementUseCase;
        this.movementMapper = movementMapper;
    }
    
    @PostMapping("/crearMovimiento")
    public ResponseEntity<Movement> registerMovement(@RequestBody Movement movement){
        Movement register = movementUseCase.registerMovement(movement.getAccount().getAccountNumber(), movement);
        
        return ResponseEntity.ok(register);
    }
    
    @GetMapping("/{accountNumber}")
    public ResponseEntity<List<MovementResponseDTO>> getAccountforDate(@PathVariable Long accountNumber, @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to){
        List<Movement> movements = movementUseCase.getMovementForAccountAndDate(accountNumber, from, to);
        List<MovementResponseDTO> responseDTOs = movementMapper.toDtoList(movements);
        return ResponseEntity.ok(responseDTOs);
    }
}
