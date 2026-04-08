var module = angular.module("creditcard");

module.service("cardService", function($http){

	this.getCards = function(cedula){
		return $http.get("/CreditCardWeb/rest/creditcard/list/"+cedula);
	};
	
	this.saveCard = function(client, label, mount,dateCut){
		
		var card = {
				"client":client, 
				"label":label, 
				"mount":mount, 
				"dateCut":dateCut, 
				"bonus":0, 
				"status":0
		};
		
		return $http.put("/CreditCardWeb/rest/creditcard/save", card);
	};
	
	this.deleteCard = function(id){
		return $http.delete("/CreditCardWeb/rest/creditcard/delete/" + id);
	};
});