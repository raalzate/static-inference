
package banco_hexagonal.pruebatecnica.infraestructure.Adapter.Web;
import banco_hexagonal.pruebatecnica.domain.Model.Client;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import banco_hexagonal.pruebatecnica.application.Port.In.ClientUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import jakarta.validation.Valid;

/**
 * @since 02/08/2025
 * @author AustinSalguero
 */
@RestController
@RequestMapping("/clientes")
public class ClientController {
    private final ClientUseCase clienteUseCase;
    
    public ClientController(ClientUseCase clienteUseCase){
        this.clienteUseCase = clienteUseCase;
    }
    
    @GetMapping
    public List<Client> getClientAll(){
        return clienteUseCase.getAllClient();   
    }
    
    @PostMapping("/crearCliente")
    @ResponseStatus(HttpStatus.CREATED)
    public Client createClient(@Valid @RequestBody Client client) {
        return clienteUseCase.createClient(client);
    }
    
    @GetMapping("/obtenerClienteId/{id}")
    public ResponseEntity<Client> getClientById(@PathVariable Long id){
        return ResponseEntity.ok(clienteUseCase.getClientById(id));
    }
    
    @PutMapping("/actualizarCliente/{id}")
    public ResponseEntity<Client> putClient(@PathVariable Long id, @RequestBody Client cliente){
        return ResponseEntity.ok(clienteUseCase.updateClient(id, cliente));
    }
    
    @DeleteMapping("/eliminarCliente/{id}")
    public ResponseEntity<Void> deleteClient(@PathVariable Long id){
        clienteUseCase.deleteClient(id);
        return ResponseEntity.noContent().build();
    } 
}
