package com.redhat.refarch.wildfly.swarm.lambdaair.edge.mapping;

import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

public abstract class AbstractMapper implements Mapper
{
	private static Logger logger = Logger.getLogger( AbstractMapper.class.getName() );

	public String getServiceName(HttpServletRequest request)
	{
		String fullPath = request.getPathInfo();
		logger.fine( "Path " + fullPath );

		if( fullPath == null || fullPath.length() == 0 )
		{
			return null;
		}
		else
		{
			int separatorIndex = fullPath.indexOf( "/", 1 );
			logger.fine( "Service name separator found at position " + separatorIndex );
			if( separatorIndex > 0 )
			{
				String serviceName = fullPath.substring( 1, separatorIndex );
				logger.fine( "Service name determined as " + serviceName );
				return serviceName;
			}
			else
			{
				return null;
			}
		}
	}

	public String getRoutedAddress(HttpServletRequest request, String serviceAddress)
	{
		String fullPath = request.getPathInfo();
		if( fullPath == null || fullPath.length() == 0 )
		{
			return null;
		}
		else
		{
			int separatorIndex = fullPath.indexOf( "/", 1 );
			if( separatorIndex > 0 )
			{
				String servicePath = fullPath.substring( separatorIndex );
				String url = serviceAddress + servicePath;

				String query = request.getQueryString();
				logger.fine( "Query " + query );
				if( query != null )
				{
					url += "?" + query;
				}
				logger.fine( "Returning full URL " + url );
				return url;
			}
			else
			{
				return null;
			}
		}
	}
}
