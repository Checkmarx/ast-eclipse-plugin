package com.checkmarx.eclipse.devassist.utils;

/**
 * Enumeration representing various scanning engines supported by the system.
 * Each constant signifies a specific type of scanning capability provided by the platform.
 *
 * The available scanning engines are:
 * - OSS: Represents scanning for Open Source Software dependencies and vulnerabilities.
 * - SECRETS: Represents scanning for sensitive information such as secrets and credentials in the code.
 * - CONTAINERS: Represents scanning for vulnerabilities in container images.
 * - IAC: Represents scanning for Infrastructure as Code issues and misconfigurations.
 * - ASCA: Represents scanning for Application Security Code Analysis.
 */
public enum ScanEngine {
	OSS,
	SECRETS,
	CONTAINERS,
	IAC,
	ASCA,
	ALL
}
