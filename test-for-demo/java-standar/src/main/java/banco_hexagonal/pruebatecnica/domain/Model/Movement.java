
package banco_hexagonal.pruebatecnica.domain.Model;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.*;

/**
 *
 * @author AustinSalguero
 */
@Entity
@Table(name = "movements")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Movement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long movementId;
    private LocalDate date;
    private String movementType;
    private Double value;
    private Double balance;
    
    @ManyToOne
    @JoinColumn(name = "accountNumber")
    private Account account;
    
}
