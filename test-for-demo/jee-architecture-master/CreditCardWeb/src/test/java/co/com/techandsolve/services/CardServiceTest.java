package co.com.techandsolve.services;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import co.com.techandsolve.creditcard.business.CardBusiness;
import co.com.techandsolve.creditcard.entities.Card;
import co.com.techandsolve.creditcard.entities.Client;
import co.com.techandsolve.creditcard.services.CardService;

@RunWith(MockitoJUnitRunner.class)
public class CardServiceTest {

	private static final String CEDULA = "1234";

	@Mock
	private CardBusiness cardBusinnes;

	@InjectMocks
	private CardService cardService;

	@Test
	public void debeListarLasTarjetas() throws Exception {
		// arrange

		List<Card> expected = new ArrayList<Card>();

		Card card1 = new Card();
		card1.setMount(1000000);
		expected.add(card1);

		Mockito.when(cardBusinnes.getListAndAppenndBonus(CEDULA)).thenReturn(
				expected);

		// act

		List<Card> resultList = cardService.cardList(CEDULA);
		// asserts

		Assert.assertEquals(expected, resultList);
		Mockito.verify(cardBusinnes).getListAndAppenndBonus(CEDULA);
	}

	@Test
	public void debeCrearTarjeta() {

		// arrange
		Card card = new Card();
		card.setBonus(0);
		card.setStatus(0);
		card.setLabel("Tarjeta Visa Express");
		card.setMount(58000000);
		card.setClient(new Client());

		// act

		cardService.cardSave(card);
		// assert

		Mockito.verify(cardBusinnes).createCard(card);
	}

	@Test
	public void debeEliminarTarjeta() {
		// arrange
		int id = 1;

		// act
		cardService.cardDelete(id);

		// assert
		Mockito.verify(cardBusinnes).deleteCard(id);
	}
}
