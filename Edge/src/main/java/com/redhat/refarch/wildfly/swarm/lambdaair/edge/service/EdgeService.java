package com.redhat.refarch.wildfly.swarm.lambdaair.edge.service;

import com.redhat.refarch.wildfly.swarm.lambdaair.edge.mapping.MappingConfiguration;

import org.apache.http.client.utils.URIUtils;
import org.mitre.dsmiley.httpproxy.ProxyServlet;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet( name = "Edge", urlPatterns = "/*" )
public class EdgeService extends ProxyServlet
{
	private static Logger logger = Logger.getLogger( EdgeService.class.getName() );
	private static final String ATTR_FULL_URI = EdgeService.class.getName() + ".query";

	@Inject
	private MappingConfiguration mapping;

	@Override
	protected String rewriteUrlFromRequest(HttpServletRequest servletRequest)
	{
		return (String)servletRequest.getAttribute( ATTR_FULL_URI );
	}

	@Override
	protected void initTarget() throws ServletException
	{
		//No target URI used
	}

	@Override
	protected void service(HttpServletRequest servletRequest, HttpServletResponse servletResponse) throws ServletException, IOException
	{
		try
		{
			String fullAddress = mapping.getHostAddress( servletRequest );
			URI uri = new URI( fullAddress );
			logger.fine( "Will forward request to " + fullAddress );
			servletRequest.setAttribute( ATTR_TARGET_HOST, URIUtils.extractHost( uri ) );
			servletRequest.setAttribute( ATTR_FULL_URI, uri.toString() );
			URI noQueryURI = new URI( uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), uri.getPath(), null, null );
			servletRequest.setAttribute( ATTR_TARGET_URI, noQueryURI.toString() );
			super.service( servletRequest, servletResponse );
		}
		catch( URISyntaxException e )
		{
			throw new ServletException( e );
		}
	}
}