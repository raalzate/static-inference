
package banco_hexagonal.pruebatecnica.application.service;

import banco_hexagonal.pruebatecnica.application.Port.In.ReportUseCase;
import banco_hexagonal.pruebatecnica.domain.Exception.EntidadNoEncontradaException;
import banco_hexagonal.pruebatecnica.domain.Model.Account;
import banco_hexagonal.pruebatecnica.domain.Model.Client;
import banco_hexagonal.pruebatecnica.domain.Model.Movement;
import banco_hexagonal.pruebatecnica.infraestructure.Adapter.Persistence.AccountRepositoryJpa;
import banco_hexagonal.pruebatecnica.infraestructure.Adapter.Persistence.ClientRepositoryJpa;
import banco_hexagonal.pruebatecnica.infraestructure.Adapter.Persistence.MovementRepositoryJpa;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 *
 * @author AustinSalguero
 */
@Service
public class ReportService implements ReportUseCase{
    public final ClientRepositoryJpa clientRepository;
    public final AccountRepositoryJpa accountRepository;
    public final MovementRepositoryJpa movementRepository;
   
    public ReportService(ClientRepositoryJpa clientRepository, AccountRepositoryJpa accountRepository, MovementRepositoryJpa movementRepository){
        this.clientRepository = clientRepository;
        this.accountRepository = accountRepository;
        this.movementRepository = movementRepository;
    }    
    
    @Override
    public Map<Account, List<Movement>> generateReport(Long clientId, LocalDate fromDate, LocalDate toDate) {
        Client client = clientRepository.findById(clientId).orElseThrow(() -> new EntidadNoEncontradaException("Cliente no se encuentra registrado."));
        List<Account> accounts = accountRepository.findAll()
                .stream()
                .filter(c -> c.getClient()
                        .getClientId().equals(clientId)).collect(Collectors.toList());
        Map<Account, List<Movement>> report = new LinkedHashMap<>();
        for(Account account : accounts){
         List<Movement> movements = movementRepository.findByAccountAndDateBetween(account, fromDate, toDate);
         report.put(account, movements);
    }
    return report;
}
}
