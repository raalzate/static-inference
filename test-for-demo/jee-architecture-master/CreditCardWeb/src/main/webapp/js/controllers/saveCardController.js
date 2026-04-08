var module = angular.module("creditcard");


module.controller("saveCardController", function($scope, cardService, clientFactory, $location){	
	
	setup();//start
	
	function setup(){
		if(validClient()){
			$scope.cedula = clientFactory.client.cedula;
			$scope.nameClient =  clientFactory.client.name;
		} else {
			$location.url("/");
		}
	}
	
	function validClient(){
		return clientFactory.client.id?true:false;
	}
	
	$scope.onClickSaveCard = function(){
		
		var dateCut = moment($scope.dateCut).format("YYYY-MM-DD");

		var saveCard = cardService.saveCard(
				clientFactory.client, $scope.label, $scope.mount, dateCut);

		saveCard.success(function(data){
			alert("Se creo la tarjeta correctamente!.");
			$location.url("/");
		}).error(function(data, status, headers, config){
			alert(headers("internalServerError"));
		});
	};
	
	$scope.onPressBack = function(){
		$location.url("/");
	};
});