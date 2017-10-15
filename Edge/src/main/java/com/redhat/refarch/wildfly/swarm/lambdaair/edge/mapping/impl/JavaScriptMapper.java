package com.redhat.refarch.wildfly.swarm.lambdaair.edge.mapping.impl;

import com.redhat.refarch.wildfly.swarm.lambdaair.edge.mapping.AbstractMapper;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.servlet.http.HttpServletRequest;

public class JavaScriptMapper extends AbstractMapper
{
	private static Logger logger = Logger.getLogger( JavaScriptMapper.class.getName() );
	private static final String JS_FILE_NAME = "/edge/routing.js";
	private static final JavaScriptMapper INSTANCE = new JavaScriptMapper();
	private ScriptEngine engine;

	public static JavaScriptMapper getInstance()
	{
		return INSTANCE;
	}

	private JavaScriptMapper()
	{
	}

	@Override
	public boolean initialize()
	{
		engine = new ScriptEngineManager( null ).getEngineByName( "JavaScript" );
		logger.fine( "JavaScript Engine for mapper is " + engine.getFactory().getEngineName() + " " + engine.getFactory().getEngineVersion() );
		try
		{
			FileReader fileReader = new FileReader( JS_FILE_NAME );
			engine.getBindings( ScriptContext.GLOBAL_SCOPE ).put( "mapper", this );
			((Compilable)engine).compile( fileReader );
			return true;
		}
		catch( FileNotFoundException e )
		{
			logger.log( Level.FINE, JS_FILE_NAME + " JavaScript routing rules not found", e );
			return false;
		}
		catch( ScriptException e )
		{
			logger.log( Level.WARNING, "Failed to compile JavaScript " + JS_FILE_NAME, e );
			return false;
		}
	}

	@Override
	public String getHostAddress(HttpServletRequest request, String hostAddress)
	{
		try
		{
			FileReader fileReader = new FileReader( JS_FILE_NAME );

			Bindings bindings = engine.createBindings();
			bindings.put( "request", request );
			bindings.put( "hostAddress", hostAddress );

			engine.eval( fileReader, bindings );

			return (String)bindings.get( "hostAddress" );
		}
		catch( FileNotFoundException e )
		{
			logger.log( Level.WARNING, "Error reading " + JS_FILE_NAME, e );
			return hostAddress;
		}
		catch( ScriptException e )
		{
			logger.log( Level.WARNING, "Failed to map host address through JavaScript", e );
			return hostAddress;
		}
	}

	public String getBaggageItem(HttpServletRequest request, String key)
	{
		return request.getHeader( "uberctx-" + key );
	}

	public void info(Object message)
	{
		logger.info( String.valueOf( message ) );
	}

	public void fine(Object message)
	{
		logger.fine( String.valueOf( message ) );
	}
}

