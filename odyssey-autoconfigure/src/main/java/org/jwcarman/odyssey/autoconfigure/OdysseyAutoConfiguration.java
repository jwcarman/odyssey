package org.jwcarman.odyssey.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@EnableConfigurationProperties(OdysseyProperties.class)
public class OdysseyAutoConfiguration {}
