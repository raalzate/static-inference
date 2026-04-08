package co.com.techandsolve.creditcard.bean;

import java.util.List;

import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;

import co.com.techandsolve.creditcard.entities.Card;

@Stateless
public class CardBean {

	@PersistenceContext
	private EntityManager entityManager;
	
	
	public List<Card> getCardByCC(String cedulaCliente) {
		
		TypedQuery<Card> query= entityManager
				.createNamedQuery("Card.findAll", Card.class);
		
	    query.setParameter("cedula", cedulaCliente);
	   
		return query.getResultList();
		
	}


	public List<Card> addBonus(String cedula, double bonus) {
		TypedQuery<Card> query= entityManager
				.createNamedQuery("Card.updateBonus", Card.class);
		
	    query.setParameter("cedula", cedula);
	    query.setParameter("bonus", bonus);

		return query.getResultList();
	}


	public void create(Card card) {
		entityManager.persist(card);
	}


	public void delete(int id) {
		Card retrieveCard = entityManager.find(Card.class, id);
		entityManager.remove(retrieveCard);
	}

}
