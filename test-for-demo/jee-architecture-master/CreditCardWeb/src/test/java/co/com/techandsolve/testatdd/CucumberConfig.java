package co.com.techandsolve.testatdd;


import org.junit.runner.RunWith;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;



@RunWith(Cucumber.class)
@CucumberOptions(strict = true, 
				features = { "src/test/resources/cucumber/", },
				glue={"co.com.techandsolve.testatdd"},
				tags = {"@guardarFactura, @listar, @guardar"})
public class CucumberConfig {

}
