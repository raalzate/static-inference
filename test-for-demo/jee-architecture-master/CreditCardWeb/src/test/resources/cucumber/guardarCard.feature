Feature:Guardar tarjeta

@guardar
Scenario: guardar una tarjeta
	Given Dado que el usuario ingresa a la aplicacion para guardar una tarjeta
	When El usuario busque con la cedula 11111 y da click en el boton nuevo
	Then debe registrar la tarjeta con el nombre "Tarjeta Master Card" con el monto de 500000 y en la fecha 2016-10-10
	Then debe visualizar una dialog con el mensaje "Se creo la tarjeta correctamente!."

