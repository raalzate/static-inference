var module = angular.module("creditcard");

module.service("billService", function($http){

	this.saveBill = function(client, label, mount,dateCut){
		
		var bill = {
				"label":client, 
				"description":label, 
				"mount":mount
		};
		
		return $http.put("/CreditCardWeb/rest/bill/register", bill);
	};
	
});