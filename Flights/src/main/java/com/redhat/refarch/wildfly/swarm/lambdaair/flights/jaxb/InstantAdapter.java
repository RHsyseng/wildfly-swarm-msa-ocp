package com.redhat.refarch.wildfly.swarm.lambdaair.flights.jaxb;

import java.time.Instant;

import javax.xml.bind.annotation.adapters.XmlAdapter;

public class InstantAdapter extends XmlAdapter<Long, Instant>
{
	@Override
	public Instant unmarshal(Long json) throws Exception
	{
		return Instant.ofEpochSecond( json );
	}

	@Override
	public Long marshal(Instant instant) throws Exception
	{
		return instant.getEpochSecond();
	}
}