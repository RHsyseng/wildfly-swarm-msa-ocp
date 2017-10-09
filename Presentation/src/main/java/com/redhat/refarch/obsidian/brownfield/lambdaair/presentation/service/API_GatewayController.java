package com.redhat.refarch.obsidian.brownfield.lambdaair.presentation.service;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixThreadPoolKey;
import com.redhat.refarch.obsidian.brownfield.lambdaair.presentation.model.Airport;
import com.redhat.refarch.obsidian.brownfield.lambdaair.presentation.model.Flight;
import com.redhat.refarch.obsidian.brownfield.lambdaair.presentation.model.FlightSegment;
import com.redhat.refarch.obsidian.brownfield.lambdaair.presentation.model.Itinerary;

import org.wildfly.swarm.spi.runtime.annotations.ConfigurationValue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import io.opentracing.ActiveSpan;
import io.opentracing.NoopActiveSpanSource;
import io.opentracing.util.GlobalTracer;
import rx.Observable;

import static com.redhat.refarch.obsidian.brownfield.lambdaair.presentation.service.RestClient.getWebTarget;
import static com.redhat.refarch.obsidian.brownfield.lambdaair.presentation.service.RestClient.invokeGet;
import static com.redhat.refarch.obsidian.brownfield.lambdaair.presentation.service.RestClient.invokePost;

@Path("/")
@RequestScoped
public class API_GatewayController
{
	private static Logger logger = Logger.getLogger( API_GatewayController.class.getName() );

	@Inject
	@ConfigurationValue( "hystrix.threadpool.SalesThreads.coreSize" )
	private int threadSize;

	@GET
	@Path("/airportCodes")
	@Produces( MediaType.APPLICATION_JSON)
	public String[] airports() throws HttpErrorException, ProcessingException
	{
		GlobalTracer.get().activeSpan().setTag( "Operation", "Look Up Airport Codes" );//TODO inject?
		WebTarget webTarget = getWebTarget( "airports", "airports" );
		Airport[] airports = invokeGet( webTarget, Airport[].class );
		String[] airportDescriptors = new String[airports.length];
		for( int index = 0; index < airportDescriptors.length; index++ )
		{
			Airport airport = airports[index];
			airportDescriptors[index] = airport.getCode() + "\t" + airport.getCity() + " - " + airport.getName();
		}
		return airportDescriptors;
	}

	@GET
	@Path("/query")
	@Produces( MediaType.APPLICATION_JSON)
	public List<Itinerary> query(@QueryParam( "departureDate" ) String departureDate, @QueryParam( "returnDate" ) String returnDate, @QueryParam( "origin" ) String origin, @QueryParam( "destination" ) String destination, @Context HttpServletRequest request) throws HttpErrorException, ProcessingException
	{
		ActiveSpan querySpan = GlobalTracer.get().activeSpan(); //TODO inject tracer?
		querySpan.setTag( "Operation", "Itinerary Query" );
		querySpan.setBaggageItem( "forwarded-for", request.getHeader( "x-forwarded-for" ) );
		long queryTime = System.currentTimeMillis();
		WebTarget webTarget = getWebTarget( "flights", "query" );
		webTarget = webTarget.queryParam( "date", departureDate );
		webTarget = webTarget.queryParam( "origin", origin );
		webTarget = webTarget.queryParam( "destination", destination );
		Flight[] departingFlights = invokeGet( webTarget, Flight[].class );
		logger.info( "Found " + departingFlights.length + " departing flights" );
		Map<String, Airport> airports = getAirportMap();
		populateFormattedTimes( departingFlights, airports );
		List<Itinerary> departingItineraries = getPricing( departingFlights );
		List<Itinerary> pricedItineraries;
		if( returnDate == null )
		{
			pricedItineraries = departingItineraries;
		}
		else
		{
			webTarget = webTarget.queryParam( "date", returnDate );
			webTarget = webTarget.queryParam( "origin", destination );
			webTarget = webTarget.queryParam( "destination", origin );
			Flight[] returnFlights = invokeGet( webTarget, Flight[].class );
			logger.info( "Found " + returnFlights.length + " returning flights" );
			populateFormattedTimes( returnFlights, airports );
			List<Itinerary> returningItineraries = getPricing( departingFlights );
			pricedItineraries = new ArrayList<>();
			for( Itinerary departingItinerary : departingItineraries )
			{
				for( Itinerary returningItinerary : returningItineraries )
				{
					Itinerary itinerary = new Itinerary( departingItinerary.getFlights()[0], returningItinerary.getFlights()[0] );
					itinerary.setPrice( departingItinerary.getPrice() + returningItinerary.getPrice() );
					pricedItineraries.add( itinerary );
				}
			}
		}
		pricedItineraries.sort( Itinerary.durationComparator );
		pricedItineraries.sort( Itinerary.priceComparator );
		logger.info( "Returning " + pricedItineraries.size() + " flights" );
		logger.info("Query method took " + (System.currentTimeMillis() - queryTime) + " milliseconds in total!" );
		return pricedItineraries;
	}

	private @NotNull List<Itinerary> getPricing(Flight[] itineraries)
	{
		try( ActiveSpan pricingSpan = GlobalTracer.get().buildSpan( "Itinerary Pricing" ).startActive() ) //TODO inject tracer?
		{
			long pricingTime = System.currentTimeMillis();
			List<Itinerary> pricedItineraries = new ArrayList<>();
			for( int index = 0; index < itineraries.length; )
			{
				List<Observable<Itinerary>> observables = new ArrayList<>();
				int batchLimit = Math.min( index + threadSize, itineraries.length );
				for( int batchIndex = index; batchIndex < batchLimit; batchIndex++ )
				{
					observables.add( new PricingCall( itineraries[batchIndex], pricingSpan ).toObservable() );
				}
				logger.info("Will price a batch of " + observables.size() + " tickets");
				Observable<Itinerary[]> zipped = Observable.zip( observables, objects->
				{
					Itinerary[] priced = new Itinerary[objects.length];
					for( int batchIndex = 0; batchIndex < objects.length; batchIndex++ )
					{
						priced[batchIndex] = (Itinerary)objects[batchIndex];
					}
					return priced;
				} );
				Collections.addAll( pricedItineraries, zipped.toBlocking().first() );
				index += threadSize;
			}
			logger.info("It took " + (System.currentTimeMillis() - pricingTime) + " milliseconds to price "  + itineraries.length + " tickets");
			return pricedItineraries;
		}
	}

	private Map<String, Airport> getAirportMap() throws HttpErrorException, ProcessingException
	{
		WebTarget webTarget = getWebTarget( "airports", "airports" );
		Airport[] airports = invokeGet( webTarget, Airport[].class );
		return Arrays.stream( airports ).collect( Collectors.toMap( Airport::getCode, airport -> airport ) );
	}

	private static void populateFormattedTimes(Flight[] flights, Map<String, Airport> airports)
	{
		for( Flight flight : flights )
		{
			for( FlightSegment segment : flight.getSegments() )
			{
				segment.setFormattedDepartureTime( getFormattedTime( segment.getDepartureTime(), airports.get( segment.getDepartureAirport() ) ) );
				segment.setFormattedArrivalTime( getFormattedTime( segment.getArrivalTime(), airports.get( segment.getArrivalAirport() ) ) );
			}
		}
	}

	private static String getFormattedTime(Instant departureTime, Airport airport)
	{
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern( "h:mma" );
		formatter = formatter.withLocale( Locale.US );
		formatter = formatter.withZone( ZoneId.of( airport.getZoneId() ) );
		return formatter.format( departureTime );
	}

	private class PricingCall extends HystrixCommand<Itinerary>
	{
		private Flight flight;
		private ActiveSpan.Continuation continuation;

		PricingCall(Flight flight, ActiveSpan activeSpan)
		{
			super( HystrixCommandGroupKey.Factory.asKey( "Sales" ), HystrixThreadPoolKey.Factory.asKey( "SalesThreads" ) );
			this.flight = flight;
			this.continuation = activeSpan != null ? activeSpan.capture() : NoopActiveSpanSource.NoopContinuation.INSTANCE;
		}

		@Override
		protected Itinerary run() throws HttpErrorException, ProcessingException
		{
			try( ActiveSpan activeSpan = continuation.activate() )
			{
				WebTarget webTarget = getWebTarget( "sales", "price" );
				return invokePost( webTarget, flight, Itinerary.class );
			}
		}

		@Override
		protected Itinerary getFallback()
		{
			logger.warning( "Failed to obtain price, " + getFailedExecutionException().getMessage() + " for " + flight );
			return new Itinerary( flight );
		}
	}
}
