package co.com.techandsolve.creditcard.provider;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import co.com.techandsolve.creditcard.exception.LockedException;

@Provider
public class LockedExceptionProvider implements ExceptionMapper<LockedException>{

	@Override
	public Response toResponse(LockedException exception) {
		
		return Response.serverError()
				.header("internalServerError", exception.getMessage())
				.build();
	}

}
