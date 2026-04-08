
package banco_hexagonal.pruebatecnica.infraestructure.Adapter.Persistence;

import banco_hexagonal.pruebatecnica.domain.Model.Account;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 *
 * @author AustinSalguero
 */
public interface AccountRepositoryJpa extends JpaRepository<Account, Long>{
    
    @Query("SELECT a FROM Account a WHERE a.client.numberId = :numberId")
    List<Account> findClientByNumberId(@Param("numberId") String numberId);
    
    @Query("SELECT a FROM Account a WHERE a.accountNumber = :accountNumber")
    Optional<Account> findAccountByAccountNumber(@Param("accountNumber") Long accountNumber);
}
