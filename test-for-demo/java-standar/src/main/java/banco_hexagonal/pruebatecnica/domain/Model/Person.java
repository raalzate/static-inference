package banco_hexagonal.pruebatecnica.domain.Model;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Builder;
import lombok.Data;

/**
 *
 * @author AustinSalguero
 */
@Data
@MappedSuperclass
public abstract class Person {
    protected String name;
    protected String gender;
    protected String age;
    @Column(name = "numberId", unique = true, nullable = false)
    protected String numberId;
    protected String address;
    protected String phoneNumber;
}
