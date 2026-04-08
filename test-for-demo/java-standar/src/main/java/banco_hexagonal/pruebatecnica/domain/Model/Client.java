
package banco_hexagonal.pruebatecnica.domain.Model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 *
 * @author AustinSalguero
 */
@Entity
@Table(name="clients")
@Data
@EqualsAndHashCode(callSuper = true) 
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client extends Person{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long clientId;
    @NotBlank(message = "El email es obligatorio.")
    @Email(message = "El email debe tener un formato valido.")
    @Pattern(regexp = ".*@.*\\..*", message = "El email debe contener '@' y '.'")
    private String email;
    private String password;
    private String status = "A";
    
}
