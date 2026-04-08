
package banco_hexagonal.pruebatecnica.infraestructure.Adapter.Web;

import banco_hexagonal.pruebatecnica.application.Port.In.AccountUseCase;
import banco_hexagonal.pruebatecnica.domain.Model.Account;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *@since 02/08/2025
 * @author AustinSalguero
 */
@RestController
@RequestMapping("/cuentas")
public class AccountController {
   
    private final AccountUseCase accountUseCase;
    
    public AccountController(AccountUseCase accountUseCase){
        this.accountUseCase = accountUseCase;
    }
    
    @GetMapping("/obtenerCuentas")
    public List<Account> getAllAccounts(){
        return accountUseCase.getAllAccounts();   
    }
    
     @GetMapping("/obtenerCuentaPorId/{accountId}")
    public ResponseEntity<Account> getAccountById(@PathVariable Long accountId){
        Account account = accountUseCase.getAccountById(accountId);
        return ResponseEntity.ok(account);
    }
    
    @PostMapping("/obtenerCuentaPorIdentificacion")
    public ResponseEntity<List<Account>> getAccountByNumberId(@RequestBody Account account){
        List<Account> accounts = accountUseCase.getAccountsByClientNumberId(account.getClient().getNumberId());
        return ResponseEntity.ok(accounts);
    }
    
    @PostMapping("/crearCuenta")
    public ResponseEntity<Account> createAccount(@RequestBody Account account){
        Account create = accountUseCase.createAccount(account.getClient().getClientId(), account);
        return ResponseEntity.ok(create);
    }
    
    @PutMapping("/modificarCuenta/{accountId}")
    public ResponseEntity<Account> updateAccount(@PathVariable Long accountId,  @RequestBody Account account){
        Account updated = accountUseCase.updateAccount(accountId, account);
        return ResponseEntity.ok(updated);
    }
    
    @DeleteMapping("eliminarCuenta/{accountNumber}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long accountNumber){
        accountUseCase.deleteAccount(accountNumber);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/obtenerCuentaPorNumeroCuenta")
    public ResponseEntity<Account> getAccountByAccountNumber(@RequestBody Account account){      
        return ResponseEntity.ok(accountUseCase.getAccountByAccountNumber(account.getAccountNumber()));
    }
}
