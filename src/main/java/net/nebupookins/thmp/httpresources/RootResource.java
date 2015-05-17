package net.nebupookins.thmp.httpresources;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import net.nebupookins.thmp.LocalSongFileDB;

import com.codahale.metrics.annotation.Timed;

import freemarker.core.ParseException;
import freemarker.template.Configuration;
import freemarker.template.MalformedTemplateNameException;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateNotFoundException;

@Path("/")
@Produces(MediaType.TEXT_HTML)
public class RootResource {
	private Configuration freemarkerCfg;

	public RootResource(Configuration freemarkerCfg) {
		this.freemarkerCfg = freemarkerCfg;
	}

	@GET
	@Timed
	public String sayHello() throws TemplateNotFoundException, MalformedTemplateNameException, ParseException, IOException, TemplateException {
		//TODO use https://dropwizard.github.io/dropwizard/manual/views.html#manual-views
		Template template = freemarkerCfg.getTemplate("index.html");
		Map<String, Object> freemarkerModel = new HashMap<>();
		StringWriter out = new StringWriter();
		template.process(freemarkerModel, out);
		return out.toString();
	}
}
