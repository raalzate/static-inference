package co.com.techandsolve.creditcard.entities;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Entity
@Table(name="card")
@NamedQueries({
    @NamedQuery(name="Card.findAll",
                query="SELECT card "
                		+ "FROM Card card  "
                		+ "WHERE card.client.cedula = :cedula"),
    @NamedQuery(name="Card.updateBonus",
                query="UPDATE Card card "
                		+ "SET bonus = :bonus "
                		+ "WHERE  card.client.cedula = :cedula "
                		+ "AND mount > 1000000")
}) 
public class Card {
	
	@Id
	private int id;
	
	@NotNull(message="label requerido")
	private String label;
	
	@Column(name="amount")
	@NotNull(message="monto requerida")
	@Min(message="el monto debe ser minimo 100", value=100)
	private double mount;
	
	private double bonus;
	
	@NotNull(message="fecha de corte requerida")
	@Column(name="cut_date")
	private String dateCut;
	
	private int status;

	@OneToOne
	@JoinColumn(name="client_id")
	private Client client;
	
	public String getDateCut() {
		return dateCut;
	}
	
	public void setDateCut(String dateCut) {
		this.dateCut = dateCut;
	}
	
	public int getStatus() {
		return status;
	}
	
	public void setStatus(int status) {
		this.status = status;
	}
	
	public double getBonus() {
		return bonus;
	}
	
	public void setBonus(double bonus) {
		this.bonus = bonus;
	}
	
	public int getId() {
		return id;
	}
	
	public void setId(int id) {
		this.id = id;
	}
	
	public String getLabel() {
		return label;
	}
	
	public void setLabel(String label) {
		this.label = label;
	}
	
	public double getMount() {
		return mount;
	}
	
	public void setMount(double mount) {
		this.mount = mount;
	}
	
	public void setClient(Client client){
		this.client = client;
	}
	
	public Client getClient(){
		return client;
	}

	
	
}
