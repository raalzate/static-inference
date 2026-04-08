package co.com.techandsolve.creditcard.provider;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import org.hibernate.validator.method.MethodConstraintViolation;
import org.hibernate.validator.method.MethodConstraintViolationException;

@SuppressWarnings("deprecation")
@Provider
public class ViolationExceptionProvider implements ExceptionMapper<MethodConstraintViolationException> {

	@Override
	public Response toResponse(MethodConstraintViolationException ex) {

		
		StringBuilder buf = new StringBuilder();
		
        for (MethodConstraintViolation<?> methodConstraintViolation : ex.getConstraintViolations()) {
        	buf.append(methodConstraintViolation.getMessage());
        	buf.append(", ");
        }

		return Response.serverError()
				.header("internalServerError", buf.toString())
				.build();
	}
	
	
	
}
