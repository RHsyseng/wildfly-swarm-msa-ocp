package com.redhat.refarch.wildfly.swarm.lambdaair.flights.service;

import com.redhat.refarch.wildfly.swarm.lambdaair.flights.model.Airport;
import com.redhat.refarch.wildfly.swarm.lambdaair.flights.model.Flight;
import com.redhat.refarch.wildfly.swarm.lambdaair.flights.model.FlightSchedule;
import com.redhat.refarch.wildfly.swarm.lambdaair.flights.model.FlightSegment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;

import io.opentracing.util.GlobalTracer;

@Path("/")
public class Controller
{
	private static Logger logger = Logger.getLogger( Controller.class.getName() );
	private DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern( "yyyyMMdd" );

	@GET
	@Path("/query")
	@Produces( MediaType.APPLICATION_JSON)
	public List<Flight> query(@QueryParam( "date" ) String date, @QueryParam( "origin" ) String origin, @QueryParam( "destination" ) String destination) throws HttpErrorException, ProcessingException
	{
		GlobalTracer.get().activeSpan().setTag( "Operation", "Look Up Flights" );
		Map<String, Airport> airports = new HashMap<>();
		WebTarget webTarget = RestClient.getWebTarget( "airports", "airports" );
		Airport[] airportArray = RestClient.invokeGet( webTarget, Airport[].class );
		for( Airport airport : airportArray )
		{
			airports.put( airport.getCode(), airport );
		}

		LocalDate travelDate = LocalDate.parse( date, dateFormatter );
		logger.info( origin + " => " + destination + " on " + travelDate );
		List<FlightSchedule[]> routes = FlightSchedulingService.getRoutes( airports, origin, destination );
		List<Flight> flights = new ArrayList<>();
		for( FlightSchedule[] route : routes )
		{
			FlightSegment[] segments = new FlightSegment[route.length];
			for( int index = 0; index < segments.length; index++ )
			{
				segments[index] = new FlightSegment();
				segments[index].setFlightNumber( Integer.parseInt( route[index].getFlightNumber() ) );
				segments[index].setDepartureAirport( route[index].getDepartureAirport() );
				segments[index].setArrivalAirport( route[index].getArrivalAirport() );
				//For now assume all travel time is for the requested date and not +1.
				segments[index].setDepartureTime( getInstant( travelDate, route[index].getDepartureTime(), airports.get( route[index].getDepartureAirport() ).getZoneId() ) );
				segments[index].setArrivalTime( getInstant( travelDate, route[index].getArrivalTime(), airports.get( route[index].getArrivalAirport() ).getZoneId() ) );
			}
			//Fix the timestamp when date is the next morning
			Instant previousTimestamp = segments[0].getDepartureTime();
			for( FlightSegment segment : segments )
			{
				if( previousTimestamp.isAfter( segment.getDepartureTime() ) )
				{
					segment.setDepartureTime( segment.getDepartureTime().plus( 1, ChronoUnit.DAYS ) );
				}
				previousTimestamp = segment.getDepartureTime();
				if( previousTimestamp.isAfter( segment.getArrivalTime() ) )
				{
					segment.setArrivalTime( segment.getArrivalTime().plus( 1, ChronoUnit.DAYS ) );
				}
				previousTimestamp = segment.getArrivalTime();
			}
			Flight flight = new Flight();
			flight.setSegments( segments );
			flights.add( flight );
		}
		for( Flight flight : flights )
		{
			if( flight.getSegments().length == 1 )
			{
				logger.info( "Nonstop:\t" + flight.getSegments()[0] );
			}
			else
			{
				logger.info( "One stop\n\t" + flight.getSegments()[0] + "\n\t" + flight.getSegments()[1] );
			}
		}
		return flights;
	}

	private Instant getInstant(LocalDate travelDate, LocalTime localTime, ZoneId zoneId)
	{
		return ZonedDateTime.of( travelDate, localTime, zoneId ).toInstant();
	}
}