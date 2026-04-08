package co.com.techandsolve.testatdd;

import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import cucumber.api.java.en.When;

public class GuardarFeature {
	WebDriver webDriver;
	
	@Before("@guardar")
	public void setup(){
		Utilidadesbd.ejecutarSentencia("DELETE FROM client WHERE id = 1;");
		Utilidadesbd.ejecutarSentencia("DELETE FROM card WHERE client_id IN "
				+ "(SELECT c.id FROM client c WHERE c.cedula = '11111');");
		
		Utilidadesbd.ejecutarSentencia("INSERT INTO client (id, name, cedula) values (1,'Raul .A Alzate', '11111');");
		Utilidadesbd.ejecutarSentencia("INSERT INTO card (label, status, amount, bonus, client_id) values ('Tarjeta Test', 0, 1500000, 0, 1);");
	}
	
	@After("@guardar")
	public void after(){
		Utilidadesbd.ejecutarSentencia("DELETE FROM card WHERE client_id IN "
				+ "(SELECT c.id FROM client c WHERE c.cedula = '11111');");
		Utilidadesbd.ejecutarSentencia("DELETE FROM client WHERE id = 1;");
		webDriver.close();
	}
	
	@Given("^Dado que el usuario ingresa a la aplicacion para guardar una tarjeta$")
	public void dado_que_el_usuario_ingresa_a_la_aplicacion_para_guardar_una_tarjeta() throws Throwable {
		webDriver = new FirefoxDriver();
		webDriver.manage().timeouts().implicitlyWait(30, TimeUnit.SECONDS);
		webDriver.get("http://localhost:8080/CreditCardWeb/");
	}
	
	
	
	@When("^El usuario busque con la cedula (\\d+) y da click en el boton nuevo$")
	public void el_usuario_busque_con_la_cedula_y_da_click_en_el_boton_nuevo(int cedula) throws Throwable {
		WebElement nuevoElement = webDriver.findElement(By.name("new"));
		WebElement cedulaElement = webDriver.findElement(By.name("cedula"));
		WebElement buscarElement = webDriver.findElement(By.name("buscar"));

		cedulaElement.sendKeys(cedula+"");
		buscarElement.click();
		
		webDriver.manage().timeouts().implicitlyWait(50, TimeUnit.SECONDS);

		nuevoElement.click();
	}

	@Then("^debe registrar la tarjeta con el nombre \"([^\"]*)\" con el monto de (\\d+) y en la fecha (\\d+)-(\\d+)-(\\d+)$")
	public void debe_registrar_la_tarjeta_con_el_nombre_con_el_monto_de_y_en_la_fecha(String label, int monto, int anno, int mes, int dia) throws Throwable {
		WebElement lableElement = webDriver.findElement(By.name("label"));
		WebElement montoElement = webDriver.findElement(By.name("mount"));
		WebElement fechaElement = webDriver.findElement(By.name("dateCut"));
		WebElement guardarElement = webDriver.findElement(By.name("save"));

		montoElement.sendKeys(monto+"");
		lableElement.sendKeys(label);
		fechaElement.sendKeys(anno+"-"+mes+"-"+dia);
		guardarElement.click();
	}
	
	
	@Then("^debe visualizar una dialog con el mensaje \"([^\"]*)\"$")
	public void debe_visualizar_una_dialog_con_el_mensaje(String mensaje) throws Throwable {
		try { 
			WebDriverWait wait = new WebDriverWait(webDriver, 2000);
			wait.until(ExpectedConditions.alertIsPresent()); 
			Alert alert = webDriver.switchTo().alert(); 
			Assert.assertEquals(mensaje, alert.getText());
			alert.accept();
			} catch (Exception e) { 
				//exception handling } 
		}
		
		
	}

}
