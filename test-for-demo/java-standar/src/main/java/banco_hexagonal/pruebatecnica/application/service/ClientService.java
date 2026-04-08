
package banco_hexagonal.pruebatecnica.application.service;

import banco_hexagonal.pruebatecnica.domain.Exception.EntidadNoEncontradaException;
import banco_hexagonal.pruebatecnica.domain.Model.Client;
import java.util.List;
import org.springframework.stereotype.Service;
import banco_hexagonal.pruebatecnica.application.Port.In.ClientUseCase;
import banco_hexagonal.pruebatecnica.infraestructure.Adapter.Persistence.ClientRepositoryJpa;

/**
 *
 * @author AustinSalguero
 */
@Service
public class ClientService implements ClientUseCase {
    private final ClientRepositoryJpa clienteRepository;
    
    public ClientService(ClientRepositoryJpa clienteRepository){
        this.clienteRepository = clienteRepository;
    }

    @Override
    public Client createClient(Client cliente) {
        return clienteRepository.save(cliente);
    }

    @Override
    public List<Client> getAllClient() {
        return clienteRepository.findAll();
    }

    @Override
    public Client getClientById(Long id) {
        return clienteRepository.findById(id).orElseThrow(() -> new EntidadNoEncontradaException("cliente con ID " + id + " no existe."));
    }

    @Override
    public Client updateClient(Long id, Client client) {
        Client exist = clienteRepository.findById(id)
    .orElseThrow(() -> new EntidadNoEncontradaException("Cliente con ID " + id + " no existe"));
        client.setClientId(id);
        return clienteRepository.save(client);
    }

    @Override
    public void deleteClient(Long id) {
        Client exist = clienteRepository.findById(id)
    .orElseThrow(() -> new EntidadNoEncontradaException("Cliente con ID " + id + " no existe"));

       clienteRepository.deleteById(id);
    }
    
}
