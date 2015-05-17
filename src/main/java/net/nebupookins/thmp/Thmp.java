package net.nebupookins.thmp;

import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import io.dropwizard.Application;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

import java.io.File;
import java.util.concurrent.ExecutorService;

import net.nebupookins.thmp.healthchecks.MapDbHealthCheck;
import net.nebupookins.thmp.httpresources.RootResource;
import net.nebupookins.thmp.httpresources.SongController;

import org.mapdb.DBMaker;
import org.mapdb.TxMaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.module.mrbean.MrBeanModule;

public class Thmp extends Application<ThmpConfig> {
	public static void main(String[] args) throws Exception {
		new Thmp().run(args);
	}

	@Override
	public void initialize(Bootstrap<ThmpConfig> bootstrap) {
		bootstrap.getObjectMapper().registerModule(new MrBeanModule());
		super.initialize(bootstrap);
	}

	@Override
	public void run(ThmpConfig config, Environment env) throws Exception {
		File freemarkerDir = new File(config.getFreemarkerTemplateDir());
		if (!freemarkerDir.exists()) {
			throw new IllegalStateException(String.format(
					"Could not find freemarker directory: %s.",
					freemarkerDir.getAbsolutePath()));
		}
		Configuration freemarkerCfg = new Configuration(
				Configuration.VERSION_2_3_22);
		freemarkerCfg.setDirectoryForTemplateLoading(freemarkerDir);
		freemarkerCfg.setDefaultEncoding("UTF-8");
		freemarkerCfg
				.setTemplateExceptionHandler(config.isDevMode() ? TemplateExceptionHandler.HTML_DEBUG_HANDLER
						: TemplateExceptionHandler.RETHROW_HANDLER);

		TxMaker txMaker = DBMaker.newFileDB(new File("thmp.db")).closeOnJvmShutdown()
				.checksumEnable().makeTxMaker();
		LocalSongFileDB localSongDb = new LocalSongFileDB(txMaker, new SafeObjectMapper(env.getObjectMapper()));
		env.healthChecks().register(MapDbHealthCheck.NAME, new MapDbHealthCheck(txMaker));
		env.jersey().register(new RootResource(freemarkerCfg));
		env.jersey().register(new SongController(localSongDb));
		
		ExecutorService es = env.lifecycle().executorService("worker").build();
		es.execute(new ScanHardriveForMusic(localSongDb));
	}

}
