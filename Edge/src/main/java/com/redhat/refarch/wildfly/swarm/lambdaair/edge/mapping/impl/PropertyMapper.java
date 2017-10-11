package com.redhat.refarch.wildfly.swarm.lambdaair.edge.mapping.impl;

import com.redhat.refarch.wildfly.swarm.lambdaair.edge.mapping.Mapper;

import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

public class PropertyMapper implements Mapper
{
	private static Logger logger = Logger.getLogger( PropertyMapper.class.getName() );
	private static final PropertyMapper INSTANCE = new PropertyMapper();
	private static final String HOST_KEY_PREFIX = "edge.proxy.";
	private static final String HOST_KEY_SUFFIX = ".address";

	public static PropertyMapper getInstance()
	{
		return INSTANCE;
	}

	private PropertyMapper()
	{
	}

	@Override
	public String getHostAddress(HttpServletRequest request, String hostAddress)
	{
		String fullPath = request.getPathInfo();
		logger.fine( "Path " + fullPath );

		String[] segments = getPathSegments( fullPath );
		logger.fine( "segments [" + segments[0] + "] and [" + segments[1] + "]" );

		String host = getHost( segments[0] );
		logger.fine( "Mapped to host " + host );

		if( host == null )
		{
			logger.fine( segments[0] + " not mapped through properties" );
			return null;
		}
		else
		{
			logger.fine( "Mapped to host " + host );
			String query = request.getQueryString();
			logger.fine( "Query " + query );

			String url = host + segments[1];
			if( query != null )
			{
				url += "?" + query;
			}
			logger.fine( "Returning full URL " + url );
			return url;
		}
	}

	private String getHost(String context)
	{
		return System.getProperty( HOST_KEY_PREFIX + context + HOST_KEY_SUFFIX );
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
}
