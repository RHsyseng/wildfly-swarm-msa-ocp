package com.redhat.refarch.obsidian.brownfield.lambdaair.presentation.service;

import java.util.logging.Logger;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.opentracing.contrib.jaxrs2.client.ClientTracingFeature;

public class RestClient
{
	private static Logger logger = Logger.getLogger( API_GatewayController.class.getName() );

	private RestClient()
	{
	}

	public static WebTarget getWebTarget(String service, Object... path)
	{
		Client client = getClient();
		WebTarget target = client.target( System.getProperty( "service." + service + ".baseUrl" ) );
		for( Object part : path )
		{
			target = target.path( String.valueOf( part ) );
		}
		return target;
	}

	private static Client getClient()
	{
		Client client = ClientBuilder.newClient();
		client.register( ClientTracingFeature.class );
		return client;
	}

	public static <T> T invokeGet(WebTarget webTarget, Class<T> responseType) throws HttpErrorException, ProcessingException
	{
		Response response = webTarget.request( MediaType.APPLICATION_JSON ).get();
		return respond( response, responseType );
	}

	public static <S, T> T invokePost(WebTarget webTarget, S request, Class<T> responseType) throws HttpErrorException, ProcessingException
	{
		Entity<S> requestEntity = Entity.entity( request, MediaType.APPLICATION_JSON_TYPE );
		Response response = webTarget.request( MediaType.APPLICATION_JSON_TYPE ).post( requestEntity );
		return respond( response, responseType );
	}

	private static <T> T respond(Response response, Class<T> responseType) throws HttpErrorException
	{
		if( response.getStatus() >= 400 )
		{
			HttpErrorException exception = new HttpErrorException( response );
			logger.info( "Received an error response for the HTTP request: " + exception.getMessage() );
			throw exception;
		}
		else if( responseType.isArray() )
		{
			return response.readEntity( new GenericType<>( responseType ) );
		}
		else
		{
			return response.readEntity( responseType );
		}
	}
}