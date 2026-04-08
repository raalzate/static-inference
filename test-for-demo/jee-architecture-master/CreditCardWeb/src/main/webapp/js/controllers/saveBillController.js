var module = angular.module("creditcard");


module.controller("saveBillController", function($scope, billService, $location){	
	
	$scope.onClickSaveBill = function(){
		
		var saveBill = billService.saveBill($scope.label, $scope.description, $scope.mount);

		saveBill.success(function(data){
			alert("Se encolo la factura!.");
			$location.url("/");
		}).error(function(data, status, headers, config){
			alert(headers("internalServerError"));
		});
	};
	
	$scope.onPressBack = function(){
		$location.url("/");
	};
});