package com.redhat.refarch.obsidian.brownfield.lambdaair.presentation.service;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import feign.opentracing.hystrix.TracingConcurrencyStrategy;
import io.opentracing.util.GlobalTracer;

@WebListener
public class ApplicationInitialization implements ServletContextListener
{

	@Override
	public void contextInitialized(ServletContextEvent sce)
	{
		TracingConcurrencyStrategy.register( GlobalTracer.get() );
	}

	@Override
	public void contextDestroyed(ServletContextEvent sce)
	{
		//Nothing to do
	}
}
