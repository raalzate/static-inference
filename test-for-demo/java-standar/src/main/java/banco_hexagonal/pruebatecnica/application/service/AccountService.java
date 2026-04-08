
package banco_hexagonal.pruebatecnica.application.service;

import banco_hexagonal.pruebatecnica.application.Port.In.AccountUseCase;
import banco_hexagonal.pruebatecnica.domain.Exception.EntidadNoEncontradaException;
import banco_hexagonal.pruebatecnica.domain.Model.Account;
import banco_hexagonal.pruebatecnica.domain.Model.Client;
import banco_hexagonal.pruebatecnica.infraestructure.Adapter.Persistence.AccountRepositoryJpa;
import banco_hexagonal.pruebatecnica.infraestructure.Adapter.Persistence.ClientRepositoryJpa;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 *
 * @author AustinSalguero
 */
@Service
public class AccountService implements AccountUseCase{
        private final AccountRepositoryJpa accountRepository;
        private final ClientRepositoryJpa clientRepository;

    public AccountService(AccountRepositoryJpa accountRepository, ClientRepositoryJpa clientRepository){
        this.accountRepository = accountRepository;
        this.clientRepository = clientRepository;
    }
    
    @Override
    public Account createAccount(Long clientId, Account account) {
            Client exist = clientRepository.findById(clientId).orElseThrow(() -> new EntidadNoEncontradaException("El cliente con ID "+clientId+" no se encuentra registrado."));
            account.setClient(exist);
            return accountRepository.save(account);
    }

    @Override
    public List<Account> getAllAccounts() {
        return accountRepository.findAll();
    }
    
    @Override
    public Account getAccountById(Long accountId){
        return accountRepository.findById(accountId).orElseThrow(() -> new EntidadNoEncontradaException("No existe la cuenta con el id "+ accountId));
    }
    
    @Override
    public List<Account> getAccountsByClientNumberId(String accountNumber) {
        return accountRepository.findClientByNumberId(accountNumber);
    }
    
    @Override
    public Account getAccountByAccountNumber(Long accountNumber) {
        return accountRepository.findAccountByAccountNumber(accountNumber).orElseThrow(() -> {
            return new EntidadNoEncontradaException("No se encontro la cuenta con numero "+ accountNumber);
        }) ;
    }

    @Override
    public Account updateAccount(Long accountId, Account account) {
        //primero validar que exista la cuenta
        Account exist = accountRepository.findById(accountId)
                .orElseThrow(() -> new EntidadNoEncontradaException("No se encontro la cuenta con el ID "+accountId));
        account.setAccountId(accountId);
        account.setClient(exist.getClient());
        return accountRepository.save(account);
    }

    @Override
    public void deleteAccount(Long accountNumber) {
        accountRepository.deleteById(accountNumber);
    }
    
    
}
