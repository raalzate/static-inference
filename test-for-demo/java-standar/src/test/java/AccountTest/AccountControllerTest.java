package AccountTest;

import banco_hexagonal.pruebatecnica.PruebaTecnica;
import banco_hexagonal.pruebatecnica.application.Port.In.AccountUseCase;
import banco_hexagonal.pruebatecnica.domain.Model.Account;
import banco_hexagonal.pruebatecnica.domain.Model.Client;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
 */
@SpringBootTest(classes = PruebaTecnica.class)
@AutoConfigureMockMvc
public class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private AccountUseCase accountUseCase;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testCreateAccount() throws Exception {
        Client client = Client.builder()
                .clientId(1L)
                .build();

        Account account = Account.builder()
                .accountNumber(100001L)
                .accountType("Ahorros")
                .initialBalance(1000.0)
                .status("A")
                .client(client)
                .build();

        Mockito.when(accountUseCase.createAccount(eq(1L), any(Account.class))).thenReturn(account);

        mockMvc.perform(MockMvcRequestBuilders.post("/cuentas/crearCuenta")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(account)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value(100001L))
                .andExpect(jsonPath("$.accountType").value("Ahorros"))
                .andExpect(jsonPath("$.initialBalance").value(1000.0));
    }
    
    @Test
void testGetAllAccounts() throws Exception {
    Client client = Client.builder()
        .clientId(1L)
        .build();

    Account account1 = Account.builder()
        .accountNumber(100001L)
        .accountType("Ahorros")
        .initialBalance(1000.0)
        .status("A")
        .client(client)
        .build();

    Account account2 = Account.builder()
        .accountNumber(100002L)
        .accountType("Corriente")
        .initialBalance(2000.0)
        .status("A")
        .client(client)
        .build();

    List<Account> accountList = Arrays.asList(account1, account2);

    Mockito.when(accountUseCase.getAllAccounts()).thenReturn(accountList);

    mockMvc.perform(MockMvcRequestBuilders.get("/cuentas/obtenerCuentas")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].accountNumber").value(100001L))
        .andExpect(jsonPath("$[0].accountType").value("Ahorros"))
        .andExpect(jsonPath("$[0].initialBalance").value(1000.0))
        .andExpect(jsonPath("$[1].accountNumber").value(100002L))
        .andExpect(jsonPath("$[1].accountType").value("Corriente"))
        .andExpect(jsonPath("$[1].initialBalance").value(2000.0));
}
}
