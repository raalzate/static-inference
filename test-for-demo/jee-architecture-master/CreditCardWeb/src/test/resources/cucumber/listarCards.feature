Feature:listar tarjetas

@listar
Scenario: listar por cedula
	Given Dado que el usuario ingresa a la aplicacion
	When  El usuario busque con la cedula 11111
	Then debe aparcer el usuario 11111
	
@listar
Scenario: tarjetas bloqueadas
	Given Dado que el usuario ingresa a la aplicacion
	When El usuario busque con la cedula 22222
	Then Debe aparecer una alerta especificando el error "locked card"
	
@listar
Scenario: eliminar una tarjeta
	Given Dado que el usuario ingresa a la aplicacion
	When El usuario busque con la cedula 11111
	Then Debe eliminar la tarjeta "Tarjeta Test" y aceptar el mensaje "Desea eliminar la tarjeta?"
    Then Debe aparecer una alerta especificando mensaje exitoso "Se elimino la tarjeta"
	