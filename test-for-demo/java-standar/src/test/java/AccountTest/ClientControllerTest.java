
package AccountTest;

import banco_hexagonal.pruebatecnica.PruebaTecnica;
import banco_hexagonal.pruebatecnica.application.Port.In.ClientUseCase;
import banco_hexagonal.pruebatecnica.domain.Model.Client;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 *
 * @author AustinSalguero
 * 
 */
@SpringBootTest(classes = PruebaTecnica.class)
@AutoConfigureMockMvc
public class ClientControllerTest {
     @Autowired
    private MockMvc mockMvc;
    @MockBean
    private ClientUseCase clientUseCase;
    @Autowired
    private ObjectMapper objectMapper;
    
     @Test
    void testCreateAccount() throws Exception {
       
        
         Client client = Client.builder()
                .clientId(1L)
                .email("test@gmail.com")
                .password("testClient")                
                .build();

        Mockito.when(clientUseCase.createClient(any(Client.class))).thenReturn(client);

        mockMvc.perform(MockMvcRequestBuilders.post("/clientes/crearCliente")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(client)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("test@gmail.com"))
                .andExpect(jsonPath("$.password").value("testClient"));
    }
    
}
