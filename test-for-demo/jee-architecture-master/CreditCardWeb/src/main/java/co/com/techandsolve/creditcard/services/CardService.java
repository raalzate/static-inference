package co.com.techandsolve.creditcard.services;

import java.util.List;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.jboss.resteasy.spi.validation.ValidateRequest;

import co.com.techandsolve.creditcard.business.CardBusiness;
import co.com.techandsolve.creditcard.entities.Card;
import co.com.techandsolve.creditcard.exception.LockedException;

@Path("creditcard")
public class CardService {
	
	@Inject
	CardBusiness cardBusinnes;
    

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("list/{cedula}")
	public List<Card> cardList(@PathParam("cedula") String cedula) throws LockedException{
		return cardBusinnes.getListAndAppenndBonus(cedula);
	}


	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("save")
	@ValidateRequest
	public void cardSave(@Valid Card card) {
		 cardBusinnes.createCard(card);
	}


	@DELETE
	@Path("delete/{id}")
	public void cardDelete(@PathParam("id") int id) {
		 cardBusinnes.deleteCard(id);
	}
}
