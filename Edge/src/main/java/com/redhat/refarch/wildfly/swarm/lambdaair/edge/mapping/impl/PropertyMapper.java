package com.redhat.refarch.wildfly.swarm.lambdaair.edge.mapping.impl;

import com.redhat.refarch.wildfly.swarm.lambdaair.edge.mapping.AbstractMapper;

import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

public class PropertyMapper extends AbstractMapper
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
	public boolean initialize()
	{
		return true;
	}

	@Override
	public String getHostAddress(HttpServletRequest request, String hostAddress)
	{
		String serviceName = getServiceName( request );
		String host = getHost( serviceName );
		if( host == null )
		{
			logger.fine( serviceName + " not mapped through properties" );
			return hostAddress;
		}
		else
		{
			logger.fine( "Mapped to host " + host );
			return getRoutedAddress( request, host );
		}
	}

	private String getHost(String context)
	{
		return System.getProperty( HOST_KEY_PREFIX + context + HOST_KEY_SUFFIX );
	}
}
