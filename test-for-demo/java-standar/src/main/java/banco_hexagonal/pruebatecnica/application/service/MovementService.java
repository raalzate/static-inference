
package banco_hexagonal.pruebatecnica.application.service;

import banco_hexagonal.pruebatecnica.application.Port.In.MovementUseCase;
import banco_hexagonal.pruebatecnica.domain.Exception.EntidadNoEncontradaException;
import banco_hexagonal.pruebatecnica.domain.Exception.SaldoInsuficienteException;
import banco_hexagonal.pruebatecnica.domain.Model.Account;
import banco_hexagonal.pruebatecnica.domain.Model.Movement;
import banco_hexagonal.pruebatecnica.infraestructure.Adapter.Persistence.AccountRepositoryJpa;
import banco_hexagonal.pruebatecnica.infraestructure.Adapter.Persistence.MovementRepositoryJpa;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 *
 * @author AustinSalguero
 */
@Service
public class MovementService implements MovementUseCase{
private final AccountRepositoryJpa accountRepository;
private final MovementRepositoryJpa movementRepository;

public MovementService(AccountRepositoryJpa accountRepository, MovementRepositoryJpa movementRepository){
    this.accountRepository = accountRepository;
    this.movementRepository = movementRepository;
}
    @Override
    public Movement registerMovement(Long accountNumber, Movement movement) {
        Account account = accountRepository.findAccountByAccountNumber(accountNumber).orElseThrow(() -> new EntidadNoEncontradaException("La cuenta "+accountNumber+" no se encuentra registrada."));
        double newBalance;
        double actuallyBalance = account.getInitialBalance();
        if(movement.getMovementType().toUpperCase().equals("RETIRO")){
            newBalance = actuallyBalance - movement.getValue();
        }else {
            newBalance = actuallyBalance + movement.getValue();
        }
        
        if(newBalance < 0){
            throw new SaldoInsuficienteException("Saldo no disponible."); 
        }
        account.setInitialBalance(newBalance);
        accountRepository.save(account);
        movement.setAccount(account);
        movement.setDate(LocalDate.now());
        movement.setBalance(newBalance);
        return movementRepository.save(movement);
    }

    @Override
    public List<Movement> getMovementForAccountAndDate(Long accountNumber, LocalDate fromDate, LocalDate toDate) {
        Account account = accountRepository.findAccountByAccountNumber(accountNumber).orElseThrow(() -> new EntidadNoEncontradaException("Cuenta no encontrada."));
        return movementRepository.findByAccountAndDateBetween(account, fromDate, toDate);
    }
    
}
