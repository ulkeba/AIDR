package qa.qcri.aidr.utils;

import org.apache.log4j.Logger;

import qa.qcri.aidr.common.code.impl.BaseConfigurator;

/**
 * User: yakubenkova.elena@gmail.com Date: 29.09.14
 */
public class PersisterConfigurator extends BaseConfigurator {

	private static final Logger LOGGER = Logger.getLogger(PersisterConfigurator.class);

	public static final String configLoadFileName = "config.properties";

	private static final PersisterConfigurator instance = new PersisterConfigurator();

	private PersisterConfigurator() {
		LOGGER.info("Instantiating PersisterConfigurator.");
		this.initProperties(configLoadFileName, PersisterConfigurationProperty.values());
	}

	public static PersisterConfigurator getInstance() {
		return instance;
	}

}
