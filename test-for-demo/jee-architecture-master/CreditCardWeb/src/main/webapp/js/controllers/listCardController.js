var module = angular.module("creditcard");


module.controller("listCardController", function($scope, cardService, clientFactory, $location) {

	setup(); //start
	
	function setup(){
		if(validClient()) {
			$scope.cedula = clientFactory.client.cedula;
			searchCards($scope.cedula);
		} else {
			hiddenTable();
			hiddenButtonNew();
		} 
	}
	
    function hiddenTable(){
		document.getElementById("cardsTable")
			.style.visibility = "hidden";
	}
	
	function showTable(){
		document.getElementById("cardsTable")
		.style.visibility = "visible";
	}
	
    function hiddenButtonNew(){
		document.getElementById("btnNew")
			.style.visibility = "hidden";
	}
	
	function showButtonNew(){
		document.getElementById("btnNew")
		.style.visibility = "visible";
	}
	
	function showMessage(message){
		$scope.result = message;
	}
	
	function removeRowTable(id){
		document.getElementById('tr-'+id).remove();
		//clientFactory.removeCard(id);
	}
	 
	function updateClient(data){
		firstClientOfList = data[0].client;
		clientFactory.update(
				firstClientOfList.id, 
				firstClientOfList.name, 
				firstClientOfList.cedula,
				data
		);
	}
	
	function searchCards(cedula){
		showMessage("Cargando...");
		var cards = cardService.getCards(cedula);
	
		cards.success(function(data) {
			
			$scope.listCards = data;
			
			if (data.length > 0) {
				updateClient(data);
				showMessage("");	
				showTable();
				showButtonNew();
			} else {
				hiddenTable();
				hiddenButtonNew();
				showMessage("No hay resultados");
			}

		}).error(function(data, status, headers, config) {
			alert(headers("internalServerError"));
			showMessage("Error de la consulta");
			hiddenTable();
			hiddenButtonNew()
		});
	}
	
	function validClient(){
		return clientFactory.client.id?true:false;
	}
	
	$scope.onClickSearch = function() {
		searchCards($scope.cedula);
	};

	$scope.onClickDelete = function(id) {
		if(confirm("Desea eliminar la tarjeta?")) {
			showMessage("Cargando...");
			var cardDelete = cardService.deleteCard(id);
			
			cardDelete.success(function(data){
				
				showMessage("");
				alert("Se elimino la tarjeta");
				removeRowTable(id);
		
				
			}).error(function(data, status, headers, config){
				alert(headers("internalServerError"));
				showMessage("Error de la consulta");
			});
		}
		
	};

	$scope.onClickNew = function() {
		$location.url("/save");
	};
	
	$scope.onClickNewBill = function(){
		$location.url("/bill");
	};
});