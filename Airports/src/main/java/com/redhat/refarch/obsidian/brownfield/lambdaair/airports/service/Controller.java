package com.redhat.refarch.obsidian.brownfield.lambdaair.airports.service;

import com.redhat.refarch.obsidian.brownfield.lambdaair.airports.model.Airport;

import java.io.IOException;
import java.util.Collection;
import java.util.Locale;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.opentracing.util.GlobalTracer;

@Path("/")
public class Controller
{

	@GET
	@Path("/airports")
	@Produces(MediaType.APPLICATION_JSON)
	public Collection<Airport> airports(@QueryParam( "filter" ) String filter)
	{
		GlobalTracer.get().activeSpan().setTag( "Operation", "Look Up Airports" );//TODO inject?
		if( filter == null || filter.isEmpty() )
		{
			return AirportsService.getAirports();
		}
		else
		{
			return AirportsService.filter( filter );
		}
	}

	@GET
	@Path("/airports/{code}")
	@Produces(MediaType.APPLICATION_JSON)
	public Airport getAirport(@PathParam("code") String code)
	{
		GlobalTracer.get().activeSpan().setTag( "Operation", "Look Up Single Airport" );//TODO inject?
		return AirportsService.getAirport( code.toUpperCase( Locale.US ) );
	}
}