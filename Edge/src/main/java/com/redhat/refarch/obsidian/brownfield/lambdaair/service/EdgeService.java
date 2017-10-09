package com.redhat.refarch.obsidian.brownfield.lambdaair.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.opentracing.util.GlobalTracer;

@WebServlet( name = "Edge", urlPatterns = "/*" )
public class EdgeService extends HttpServlet
{
	private static Logger logger = Logger.getLogger( EdgeService.class.getName() );

	@Inject
	private MappingConfiguration mapping;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		logger.fine( "Caller IP address is " + GlobalTracer.get().activeSpan().getBaggageItem( "forwarded-for" ) ); //TODO inject tracer?
		String fullPath = request.getPathInfo();
		logger.fine( "Path " + fullPath );
		String[] segments = getPathSegments( fullPath );
		logger.fine( "segments [" + segments[0] + "] and [" + segments[1] + "]" );
		String query = request.getQueryString();
		logger.fine( "Query " + query );

		String url = getHost( segments[0] ) + segments[1];
		if( query != null )
		{
			url += "?" + query;
		}
		HttpURLConnection connection = (HttpURLConnection)new URL( url ).openConnection();
		Enumeration<String> headerKeys = request.getHeaderNames();
		while( headerKeys.hasMoreElements() )
		{
			String key = headerKeys.nextElement();
			Enumeration<String> values = request.getHeaders( key );
			while( values.hasMoreElements() )
			{
				String value = values.nextElement();
				logger.fine( "Request header " + key + ": " + value );
				connection.addRequestProperty( key, value );
			}
		}
		connection.setRequestMethod( "GET" );
		int responseCode = connection.getResponseCode();
		logger.info( "Received " + responseCode + " from " + url + " while forwarding" );
		for( Map.Entry<String, List<String>> headerEntry : connection.getHeaderFields().entrySet() )
		{
			if( headerEntry.getKey() == null )
			{
				logger.fine( "Response header null: " + headerEntry.getValue() );
				continue;
			}
			for( String value : headerEntry.getValue() )
			{
				logger.fine( "Response header " + headerEntry.getKey() + ": " + value );
				response.addHeader( headerEntry.getKey(), value );
			}
		}
		if( responseCode < 300 )
		{
			BufferedReader bufferedReader = new BufferedReader( new InputStreamReader( connection.getInputStream() ) );
			bufferedReader.lines().forEach( response.getWriter()::println );
		}
		else
		{
			response.sendError( responseCode, connection.getResponseMessage() );
		}
	}

	private static String[] getPathSegments(String fullPath)
	{
		String[] segments = new String[2];
		if( fullPath == null || fullPath.length() == 0 )
		{
			segments[0] = "";
			segments[1] = "";
		}
		else
		{
			int separatorIndex = fullPath.indexOf( "/", 1 );
			if( separatorIndex > 0 )
			{
				segments[0] = fullPath.substring( 1, separatorIndex );
				segments[1] = fullPath.substring( separatorIndex );
			}
			else
			{
				segments[0] = fullPath.substring( 1 );
				segments[1] = "";
			}
		}
		return segments;
	}

	@Override
	protected void doHead(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		super.doHead( request, response );
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		logger.fine( "Caller IP address is " + request.getHeader( "uberctx-forwarded-for" ) );
		String fullPath = request.getPathInfo();
		logger.fine( "Path " + fullPath );
		String[] segments = getPathSegments( fullPath );
		logger.fine( "segments [" + segments[0] + "] and [" + segments[1] + "]" );
		String query = request.getQueryString();
		logger.fine( "Query " + query );

		String url = getHost( segments[0] ) + segments[1];
		if( query != null )
		{
			url += "?" + query;
		}
		HttpURLConnection connection = (HttpURLConnection)new URL( url ).openConnection();
		Enumeration<String> headerKeys = request.getHeaderNames();
		while( headerKeys.hasMoreElements() )
		{
			String key = headerKeys.nextElement();
			Enumeration<String> values = request.getHeaders( key );
			while( values.hasMoreElements() )
			{
				String value = values.nextElement();
				logger.fine( "Request header " + key + ": " + value );
				connection.addRequestProperty( key, value );
			}
		}
		connection.setRequestMethod( "POST" );
		connection.setDoOutput( true );
		connection.getOutputStream();
		BufferedReader body = request.getReader();
		try( PrintWriter bodyWriter = new PrintWriter( new OutputStreamWriter( connection.getOutputStream() ) ) )
		{
			body.lines().forEach( bodyWriter::println );
		}
		int responseCode = connection.getResponseCode();
		logger.info( "Received " + responseCode + " from " + url + " while forwarding" );
		for( Map.Entry<String, List<String>> headerEntry : connection.getHeaderFields().entrySet() )
		{
			if( headerEntry.getKey() == null )
			{
				logger.fine( "Response header null: " + headerEntry.getValue() );
				continue;
			}
			for( String value : headerEntry.getValue() )
			{
				logger.fine( "Response header " + headerEntry.getKey() + ": " + value );
				response.addHeader( headerEntry.getKey(), value );
			}
		}
		if( responseCode < 300 )
		{
			BufferedReader bufferedReader = new BufferedReader( new InputStreamReader( connection.getInputStream() ) );
			bufferedReader.lines().forEach( response.getWriter()::println );
		}
		else
		{
			response.sendError( responseCode, connection.getResponseMessage() );
		}
	}

	private String getHost(String context)
	{
		String host = mapping.getHost( context );
		if( "sales".equals( context ) )
		{
			try
			{
				String callerIP = GlobalTracer.get().activeSpan().getBaggageItem( "forwarded-for" ); //TODO inject tracer?
				int lastDigit = Integer.parseInt( callerIP.substring( callerIP.length() - 1 ) );
				if( lastDigit % 2 == 0 )
				{
					logger.info( "Will redirect request for A/B testing" );
					host = "http://sales2:8080";
				}
			}
			catch( NumberFormatException e )
			{
				logger.log( Level.WARNING, "Failed to determine caller IP address as even or odd", e );
			}
		}
		return host;
	}

	@Override
	protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		super.doPut( request, response );
	}

	@Override
	protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		super.doDelete( request, response );
	}

	@Override
	protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		super.doOptions( request, response );
	}

	@Override
	protected void doTrace(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		super.doTrace( request, response );
	}
}