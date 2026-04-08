package co.com.techandsolve.creditcard.bean;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;







import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import co.com.techandsolve.creditcard.entities.Card;
import co.com.techandsolve.creditcard.entities.Client;

@RunWith(MockitoJUnitRunner.class)
public class CardBeanTest {

	@Mock
	EntityManager entityManager;
	
	@Mock
	TypedQuery<Card> query;
	
	@InjectMocks
	CardBean cardBean;

	private ArrayList<Card> expected;
	private Card card;
	
	public static final String CEDULA ="1234";
	
	@Before
	public void setUp(){
		 expected = new  ArrayList<Card>();
		 card = new Card();
	}
	
	@Test
	public void debeFiltrarLasTarjetasPorLaCedulaYRetornarlas(){
		
		//arrange
		expected.add(new Card()); 
		expected.add(new Card()); 
		
		Mockito.when(entityManager
				.createNamedQuery("Card.findAll", Card.class))
				.thenReturn(query);
		
		Mockito.when(query.getResultList()).thenReturn(expected);
		
		//act
		List<Card> lstResult = cardBean.getCardByCC(CEDULA);
		
		//assert
		Assert.assertEquals(expected, lstResult);
		
		Mockito.verify(query).setParameter("cedula", CEDULA);
			
	}
	
	@Test
	public void debeAgregarLaBonificacionesDe2Millones(){
		//arrange
		expected.add(new Card()); 
		expected.add(new Card()); 
		
		Mockito.when(entityManager
				.createNamedQuery("Card.updateBonus", Card.class))
				.thenReturn(query);
		Mockito.when(query.getResultList()).thenReturn(expected);
		
		//act
		cardBean.addBonus(CEDULA, 20000);

	}
	
	@Test
	public void debeCrearUnaTarjeta(){
		//arrange
		card.setBonus(0);
		card.setClient(new Client());
		card.setLabel("tarjeta visa express");
		card.setStatus(0);
		card.setMount(20000);
		card.setDateCut("YYYY/mm/dd");
		
		//act
		cardBean.create(card);
		
		//assert
		Mockito.verify(entityManager).persist(card);
		
	}
	
	@Test
	public void debeEliminarLaTarjeta_id1(){
		//arrange
		int id = 1;
		
		Mockito.when(entityManager
				.find(Card.class, card.getId()))
				.thenReturn(card);
		
		//act
		cardBean.delete(id);
		
		//assert
		Card card = Mockito.verify(entityManager)
				.find(Card.class, id);
		Mockito.verify(entityManager).remove(card);

		
	}
	
}
