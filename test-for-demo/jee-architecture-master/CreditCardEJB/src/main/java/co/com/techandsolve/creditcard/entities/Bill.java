package co.com.techandsolve.creditcard.entities;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;

@Entity
@Table(name="bill")
public class Bill implements Serializable{
	
	private static final long serialVersionUID = 1L;

	@Id
	private int id;
	
	@NotNull(message="label requerido")
	private String label;
	
	@NotNull(message="description requerido")
	private String description;
	
	@NotNull(message="monto requerida")
	private double mount;

	public Bill(){
		
	}
	
	
	public Bill(int id, String label, String description, double mount){
		this.label = label;
		this.description = description;
		this.mount = mount;
		this.id = id;
	}
	
	public Bill(String label, String description, double mount){
		this(0, label, description, mount);
	}

	public double getMount() {
		return mount;
	}


	public void setMount(double mount) {
		this.mount = mount;
	}


	public String getDescription() {
		return description;
	}


	public void setDescription(String description) {
		this.description = description;
	}


	public String getLabel() {
		return label;
	}


	public void setLabel(String label) {
		this.label = label;
	}


	public int getId() {
		return id;
	}


	public void setId(int id) {
		this.id = id;
	}
	
}
