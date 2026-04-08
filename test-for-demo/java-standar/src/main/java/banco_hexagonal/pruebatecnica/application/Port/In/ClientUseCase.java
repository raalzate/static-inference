
package banco_hexagonal.pruebatecnica.application.Port.In;

import banco_hexagonal.pruebatecnica.domain.Model.Client;
import java.util.List;

/**
 *
 * @author Trabajo
 */
public interface ClientUseCase {
    Client createClient(Client cliente);
    List<Client>getAllClient();
    Client getClientById(Long id);
    Client updateClient(Long id, Client cliente);
    void deleteClient(Long id);
}
