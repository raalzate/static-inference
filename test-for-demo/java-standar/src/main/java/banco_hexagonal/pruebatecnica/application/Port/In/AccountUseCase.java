
package banco_hexagonal.pruebatecnica.application.Port.In;

import banco_hexagonal.pruebatecnica.domain.Model.Account;
import java.util.List;

/**
 *@since 02/08/2025
 * @author AustinSalguero
 */
public interface AccountUseCase {
    
    Account createAccount(Long clientId, Account account);
    List<Account> getAllAccounts();
    Account getAccountById(Long accountId);
    Account getAccountByAccountNumber(Long accountNumber);
    Account updateAccount(Long accountId, Account account);
    void deleteAccount(Long accountNumber);
    List<Account> getAccountsByClientNumberId(String numberId);
}
