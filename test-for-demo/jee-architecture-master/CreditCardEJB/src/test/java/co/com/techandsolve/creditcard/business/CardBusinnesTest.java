package co.com.techandsolve.creditcard.business;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import com.googlecode.catchexception.CatchException;

import co.com.techandsolve.creditcard.bean.CardBean;
import co.com.techandsolve.creditcard.entities.Card;
import co.com.techandsolve.creditcard.entities.Client;
import co.com.techandsolve.creditcard.exception.LockedException;

@RunWith(MockitoJUnitRunner.class)
public class CardBusinnesTest {

	public static final String CEDULA = "1234";

	@Mock
	CardBean cardBean;

	@InjectMocks
	CardBusiness cardBusiness;

	private Card card1;
	private Card card2;
	
	@Before
	public void setup(){
		card1 = new Card();
		card2 = new Card();
	}
	
	@Test
	public void cuandoEsMasDe_1Mll_AgregarBonificar_20M() throws LockedException {
		// arrange
		List<Card> expected = new ArrayList<Card>();

		card1.setMount(1000000);
		card1.setStatus(0);
		expected.add(card1);

		card2.setMount(1000000);
		card2.setStatus(0);
		expected.add(card2);

		Mockito.when(cardBean.getCardByCC(CEDULA)).thenReturn(expected);

		// act
		List<Card> lstResult = cardBusiness.getListAndAppenndBonus(CEDULA);

		// assert
		Assert.assertTrue(lstResult.get(0).getBonus() == 20000);
		Assert.assertTrue(lstResult.get(1).getBonus() == 20000);

	}

	@Test
	public void cuandoEsMasDe_700M_AgregarBonificar_10M() throws LockedException {
		// arrange
		List<Card> expected = new ArrayList<Card>();

		card1.setMount(700000);
		card1.setStatus(0);
		expected.add(card1);

		Mockito.when(cardBean.getCardByCC(CEDULA)).thenReturn(expected);

		// act
		List<Card> lstResult = cardBusiness.getListAndAppenndBonus(CEDULA);
		
		// assert
		Assert.assertTrue(lstResult.get(0).getBonus() == 10000);

	}

	@Test
	public void cuandoTieneBonos_noAgregarBonificacion() throws LockedException {
		// arrange
		List<Card> expected = new ArrayList<Card>();

		card1.setMount(1000000);
		card1.setBonus(10);
		card1.setStatus(0);
		expected.add(card1);

		Mockito.when(cardBean.getCardByCC(CEDULA)).thenReturn(expected);

		// act
		List<Card> lstResult = cardBusiness.getListAndAppenndBonus(CEDULA);

		// assert
		Assert.assertTrue(lstResult.get(0).getBonus() == 10);

	}

	@Test
	public void cuandoEstaBloqueadaUnaTarjenta() throws LockedException {
		// arrange
		List<Card> expected = new ArrayList<Card>();

		card1.setMount(1000000);
		card1.setStatus(1);
		expected.add(card1);
		
		card2.setMount(1000000);
		card2.setStatus(0);
		expected.add(card2);


		Mockito.when(cardBean.getCardByCC(CEDULA)).thenReturn(expected);

		// act
		CatchException.catchException(cardBusiness).getListAndAppenndBonus(CEDULA);
		

		//assert
		
		Assert.assertTrue(CatchException.caughtException() instanceof LockedException);
		Assert.assertEquals("locked card", CatchException.caughtException().getMessage());
	
	}
	
	@Test
	public void cuandoSeCreaUnaTarjeta(){
		//arrange
		card1.setLabel("Tarjeta Visa Express");
		card1.setClient(new Client());
		card1.setMount(5000000);
		card1.setBonus(0);
				
		//act
		cardBusiness.createCard(card1);
		
		//assert
		
		Mockito.verify(cardBean).create(card1);
	}
	
	@Test
	public void cuandoSeEliminaUnaTarjeta(){
		//arrange
		int id = 1;
		
		
		//act
		cardBusiness.deleteCard(id);
		
		//assert
		Mockito.verify(cardBean).delete(id);
	}
	

}
