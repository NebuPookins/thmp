package net.nebupookins.thmp;

import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

import java.io.File;
import java.util.concurrent.ExecutorService;

import net.nebupookins.thmp.datastores.LocalSongFileDB;
import net.nebupookins.thmp.healthchecks.MapDbHealthCheck;
import net.nebupookins.thmp.httpresources.RootResource;
import net.nebupookins.thmp.httpresources.SongController;

import org.mapdb.DBMaker;
import org.mapdb.TxMaker;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.mrbean.MrBeanModule;

public class Thmp extends Application<ThmpConfig> {
	public static void main(String[] args) throws Exception {
		new Thmp().run(args);
	}

	@Override
	public void initialize(Bootstrap<ThmpConfig> bootstrap) {
		ObjectMapper objectMapper = bootstrap.getObjectMapper();
		objectMapper.registerModule(new MrBeanModule());
		objectMapper.registerModule(new Jdk8Module());
		objectMapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
		objectMapper.setVisibility(PropertyAccessor.GETTER, Visibility.PUBLIC_ONLY);
		
		bootstrap.addBundle(new AssetsBundle("/vendor", "/vendor"));
	}

	@Override
	public void run(ThmpConfig config, Environment env) throws Exception {
		final File freemarkerDir = new File(config.getFreemarkerTemplateDir());
		if (!freemarkerDir.exists()) {
			throw new IllegalStateException(String.format("Could not find freemarker directory: %s.",
					freemarkerDir.getAbsolutePath()));
		}
		final Configuration freemarkerCfg = new Configuration(Configuration.VERSION_2_3_22);
		freemarkerCfg.setDirectoryForTemplateLoading(freemarkerDir);
		freemarkerCfg.setDefaultEncoding("UTF-8");
		freemarkerCfg.setTemplateExceptionHandler(config.isDevMode() ? TemplateExceptionHandler.HTML_DEBUG_HANDLER
				: TemplateExceptionHandler.RETHROW_HANDLER);

		final TxMaker txMaker = DBMaker.newFileDB(new File("thmp.db")).closeOnJvmShutdown().checksumEnable().makeTxMaker();
		final SafeObjectMapper safeObjectMapper = new SafeObjectMapper(env.getObjectMapper());
		final LocalSongFileDB localSongDb = new LocalSongFileDB(txMaker, safeObjectMapper);
		env.healthChecks().register(MapDbHealthCheck.NAME, new MapDbHealthCheck(txMaker));
		env.jersey().register(new RootResource(freemarkerCfg));
		env.jersey().register(new SongController(localSongDb, safeObjectMapper));

		ExecutorService es = env.lifecycle().executorService("worker").build();
		es.execute(new ScanHardriveForMusic(localSongDb));
	}

}
