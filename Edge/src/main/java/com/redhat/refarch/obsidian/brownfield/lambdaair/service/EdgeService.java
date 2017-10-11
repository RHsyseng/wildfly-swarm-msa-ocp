package com.redhat.refarch.obsidian.brownfield.lambdaair.service;

import com.redhat.refarch.obsidian.brownfield.lambdaair.mapping.MappingConfiguration;

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
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet( name = "Edge", urlPatterns = "/*" )
public class EdgeService extends HttpServlet
{
	private static Logger logger = Logger.getLogger( EdgeService.class.getName() );

	@Inject
	private MappingConfiguration mapping;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		handle( request, response, "GET", false );
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		handle( request, response, "POST", true );
	}

	@Override
	protected void doHead(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		handle( request, response, "HEAD", false );
	}

	@Override
	protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		handle( request, response, "PUT", true );
	}

	@Override
	protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		handle( request, response, "DELETE", false );
	}

	@Override
	protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		handle( request, response, "OPTIONS", false );
	}

	@Override
	protected void doTrace(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		handle( request, response, "TRACE", false );
	}

	private void handle(HttpServletRequest request, HttpServletResponse response, String method, boolean hasBody) throws ServletException, IOException
	{
		logger.fine( "Caller IP address is " + request.getHeader( "uberctx-forwarded-for" ) );
		String url = mapping.getHostAddress( request );
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
		connection.setRequestMethod( method );
		connection.setDoOutput( hasBody );
		if( hasBody )
		{
			BufferedReader body = request.getReader();
			try( PrintWriter bodyWriter = new PrintWriter( new OutputStreamWriter( connection.getOutputStream() ) ) )
			{
				body.lines().forEach( bodyWriter::println );
			}
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
}