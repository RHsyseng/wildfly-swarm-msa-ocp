package com.redhat.refarch.obsidian.brownfield.lambdaair.sales.service;

import java.io.IOException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class ApplicationInitialization implements ServletContextListener
{

	@Override
	public void contextInitialized(ServletContextEvent sce)
	{
		try
		{
			SalesTicketingService.loadPricing();
		}
		catch( IOException e )
		{
			throw new IllegalStateException( e );
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce)
	{
		//Nothing to do
	}
}
