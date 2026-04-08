var cardModule = angular.module("creditcard",["ngRoute"]).

config(function($routeProvider){
	$routeProvider.when("/", {
		controller:'listCardController',
		templateUrl:'card/list.html'
	})
	.when("/save", {
		controller:'saveCardController',
		templateUrl:'card/save.html'
	})
	.when("/bill", {
		controller:'saveBillController',
		templateUrl:'bill/save.html'
	})
	.otherwise({redirectTo:'/'});
});


