Feature:Guardar factura

@guardarFactura
Scenario: guardar una factura
	Given Dado que el usuario ingresa a la aplicacion para guardar una factura
	When El usuario ingresa la factura con el nombre "Factura de credito (test)", con la descripcion "Valor a pagar" y con el monto de 500000
	Then el sistema visualiza un mensaje  con el nombre de "Se encolo la factura!."

