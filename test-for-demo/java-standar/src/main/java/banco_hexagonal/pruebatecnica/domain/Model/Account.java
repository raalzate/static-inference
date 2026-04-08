
package banco_hexagonal.pruebatecnica.domain.Model;

import jakarta.persistence.*;
import lombok.*;

/**
 *
 * @author AustinSalguero
 */
@Entity
@Table(name="accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long accountId;
    @Column(unique = true, nullable = false)
    private Long accountNumber;
    private String accountType;
    private Double initialBalance;
    private String status = "A";
    
    @ManyToOne
    @JoinColumn(name = "numberId")
    private Client client;
}
