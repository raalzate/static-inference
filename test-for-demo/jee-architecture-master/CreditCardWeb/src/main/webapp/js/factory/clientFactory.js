var module = angular.module("creditcard");


module.factory('clientFactory', function(){
	  return {
		  client: {name: '',cedula: '',id: ''},
		  cards:[],
		  update: function(id, name, cedula, cards) {
			  this.client.id = id;
			  this.client.name = name;
			  this.client.cedula = cedula;
			  this.cards = cards;
		  },
		  removeCard: function(id) {
		      for(var i = this.cards.length; i--;) {
		          if(this.cards[i].id === id) {
		        	  this.cards.splice(i, 1);
		          }
		      }
		  }
	  };
});