package co.com.techandsolve.creditcard.business;

import java.util.List;

import javax.inject.Inject;

import co.com.techandsolve.creditcard.bean.CardBean;
import co.com.techandsolve.creditcard.entities.Card;
import co.com.techandsolve.creditcard.exception.LockedException;

public class CardBusiness {

	private static final double SUM_MOUNT_1MLL = 1000000;
	private static final double SUM_MOUNT_700M = 700000;
	private static final double MOUNT_10M = 10000;
	private static final double MOUNT_20M = 20000;

	
	@Inject
	private CardBean cardBean;


	public List<Card> getListAndAppenndBonus(String cedula) throws LockedException{
		List<Card> listCard = cardBean.getCardByCC(cedula);
		
		double sumMount = 0;
		boolean hasBonus = false;
		boolean hasLocked = false;
		
		for(Card card : listCard) {
			sumMount += card.getMount();
			hasBonus |= card.getBonus() > 0;
			hasLocked  |= card.getStatus() ==1;
		}
		
		if(hasLocked) {
			throw new LockedException("locked card");
		}
		
		if(!hasBonus) {
			validSumMountAndAddBonus(sumMount, listCard);
		}
		
		return listCard;
	}
	

	
	private void validSumMountAndAddBonus(double sumMount, List<Card> listCard){
		if(sumMount > SUM_MOUNT_1MLL) {
			addBonus(listCard,MOUNT_20M);
		} else
		if(sumMount <= SUM_MOUNT_1MLL && sumMount >= SUM_MOUNT_700M) {
			addBonus(listCard,MOUNT_10M);
		}
	}
	
	private void addBonus(List<Card> listCard, double bonus){
		for(int i = 0; i< listCard.size(); i++) {
			listCard.get(i).setBonus(bonus);
		}
	}



	public void createCard(Card card) {
		 cardBean.create(card);
	}



	public void deleteCard(int id) {
		 cardBean.delete(id);
	}
	
	
}
