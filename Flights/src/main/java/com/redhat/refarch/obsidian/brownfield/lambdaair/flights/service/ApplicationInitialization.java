package com.redhat.refarch.obsidian.brownfield.lambdaair.flights.service;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ApplicationInitialization implements ApplicationListener<ContextRefreshedEvent>
{
	@Override
	public void onApplicationEvent(ContextRefreshedEvent contextRefreshedEvent)
	{
		try
		{
			FlightSchedulingService.loadFlightSchedule();
		}
		catch( IOException e )
		{
			throw new IllegalStateException( e );
		}
	}
}
