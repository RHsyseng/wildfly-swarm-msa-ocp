package com.redhat.refarch.wildfly.swarm.lambdaair.sales.service;

import com.redhat.refarch.wildfly.swarm.lambdaair.sales.model.Flight;
import com.redhat.refarch.wildfly.swarm.lambdaair.sales.model.Itinerary;

import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.opentracing.util.GlobalTracer;

@Path("/")
public class Controller
{
	private static Logger logger = Logger.getLogger( Controller.class.getName() );
	private DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern( "yyyyMMdd" );

	@POST
	@Path("/price")
	@Consumes( MediaType.APPLICATION_JSON)
	@Produces( MediaType.APPLICATION_JSON)
	public Itinerary price(Flight flight)
	{
		GlobalTracer.get().activeSpan().setTag( "Operation", "Determine Price for a Flight" );
		Itinerary itinerary = SalesTicketingService.price( flight );
		logger.info("Priced ticket at " + itinerary.getPrice() );
		return itinerary;
	}
}