package com.cyberark.conjur.springboot.domain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cyberark.conjur.springboot.core.env.ConjurConnectionManager;

/**
 * The type Conjur properties.
 */
public class ConjurProperties{
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ConjurProperties.class);
	
	//telemetry headers
	private String integrationName = System.getenv().getOrDefault("INTEGRATION_NAME", "SecretsManagerSpringBoot SDK");
    private String integrationType = System.getenv().getOrDefault("INTEGRATION_TYPE", "cybr-secretsmanager-springboot");
    private String integrationVersion = getSDKVersion();  // Fetch from VERSION file
    private String vendorName = System.getenv().getOrDefault("VENDOR_NAME", "CyberArk");
    
    /**
     * Gets the integration name used for telemetry.
     * 
     * @return the integration name
     */
    public String getIntegrationName() {
        return integrationName;
    }

    /**
     * Gets the integration type used for telemetry.
     * 
     * @return the integration type
     */
    public String getIntegrationType() {
        return integrationType;
    }

    /**
     * Gets the integration version used for telemetry.
     * 
     * @return the integration version
     */
    public String getIntegrationVersion() {
        return integrationVersion;
    }

    /**
     * Gets the vendor name used for telemetry.
     * 
     * @return the vendor name.
     */
    public String getVendorName() {
        return vendorName;
    }
    
    // Method to get the SDK version from the V	ERSION file
    public static String getSDKVersion() {
        try {
            Path rootDir = Paths.get("");
            Path versionFile = rootDir.resolve("VERSION");

            if (Files.exists(versionFile)) {
                String version = new String(Files.readAllBytes(versionFile)).trim();
                if (version.isEmpty()) {
                    LOGGER.info("VERSION file is empty");
                    return "unknown";
                }

                String[] parts = version.split("-");
                String versionWithoutSnapshot = parts[0];

                return versionWithoutSnapshot;
            }
            return "unknown";

        } catch (IOException e) {
            LOGGER.error("Error reading VERSION file: " + e.getMessage(), e);
            return "unknown";  // Default version in case of error
        }
    }
    
	/**
	 * The Account, can be injected with CONJUR_ACCOUNT environment variable
	 * or spring boot property: conjur.account
	 */
	private String account;

	/**
	 * The Appliance url can be injected with CONJUR_APPLIANCE_URL environment variable
	 * or spring boot property: conjur.appliance-url
	 */
	private String applianceUrl;

	/**
	 * The Auth token file.
	 */
	private String authTokenFile;

	/**
	 * The Api key can be injected with CONJUR_AUTHN_API_KEY environment variable
	 * or spring boot property: conjur.api-key
	 */
	private String authnApiKey;

	/**
	 * The Authn login.can be injected with CONJUR_AUTHN_LOGIN environment variable
	 * or spring boot property: conjur.authn-login
	 */
	private String authnLogin;

	/**
	 * The Cert file. can be injected with CONJUR_CERT_FILE environment variable
	 * or spring boot property: conjur.cert-file
	 */
	private String certFile;

	/**
	 * The Ssl certificate. can be injected with CONJUR_SSL_CERTIFICATE environment variable
	 * or spring boot property: conjur.ssl-certificate
	 */
	private String sslCertificate;

	/**
	 * The Jwt token path.
	 */
	private String jwtTokenPath;

	/**
	 * The Authn-JWT authenticatorID.
	 */
	private String authenticatorId;
	
	/**
	 * The Scan all values.
	 */
	private boolean scanAllValues;
	
	/**
	 * The Conjur mapping path.
	 */
	private String mappingPath;

	/**
	 * Gets account.
	 *
	 * @return the account
	 */
	public String getAccount() {
		return account;
	}

	/**
	 * Sets account.
	 *
	 * @param account the account
	 */
	public void setAccount(String account) {
		this.account = account;
	}

	/**
	 * Gets appliance url.
	 *
	 * @return the appliance url
	 */
	public String getApplianceUrl() {
		return applianceUrl;
	}

	/**
	 * Sets appliance url.
	 *
	 * @param applianceUrl the appliance url
	 */
	public void setApplianceUrl(String applianceUrl) {
		this.applianceUrl = applianceUrl;
	}

	/**
	 * Gets auth token file.
	 *
	 * @return the auth token file
	 */
	public String getAuthTokenFile() {
		return authTokenFile;
	}

	/**
	 * Sets auth token file.
	 *
	 * @param authToken the auth token
	 */
	public void setAuthTokenFile(String authToken) {
		this.authTokenFile = authToken;
	}

	/**
	 * Gets authn login.
	 *
	 * @return the authn login
	 */
	public String getAuthnLogin() {
		return authnLogin;
	}

	/**
	 * Sets authn login.
	 *
	 * @param authnLogin the authn login
	 */
	public void setAuthnLogin(String authnLogin) {
		this.authnLogin = authnLogin;
	}

	/**
	 * Gets cert file.
	 *
	 * @return the cert file
	 */
	public String getCertFile() {
		return certFile;
	}

	/**
	 * Sets cert file.
	 *
	 * @param certFile the cert file
	 */
	public void setCertFile(String certFile) {
		this.certFile = certFile;
	}

	/**
	 * Gets ssl certificate.
	 *
	 * @return the ssl certificate
	 */
	public String getSslCertificate() {
		return sslCertificate;
	}

	/**
	 * Sets ssl certificate.
	 *
	 * @param sslCertificate the ssl certificate
	 */
	public void setSslCertificate(String sslCertificate) {
		this.sslCertificate = sslCertificate;
	}

	/**
	 * Gets auth api key.
	 *
	 * @return the auth api key
	 */
	public String getAuthnApiKey() {
		return authnApiKey;
	}

	/**
	 * Sets auth api key.
	 *
	 * @param authnApiKey the auth api key
	 */
	public void setAuthnApiKey(String authnApiKey) {
		this.authnApiKey = authnApiKey;
	}

	/**
	 * Gets jwt token path.
	 *
	 * @return the jwt token path
	 */
	public String getJwtTokenPath() {
		return jwtTokenPath;
	}

	/**
	 * Sets jwt token path.
	 *
	 * @param jwtTokenPath the jwt token path
	 */
	public void setJwtTokenPath(String jwtTokenPath) {
		this.jwtTokenPath = jwtTokenPath;
	}

	/**
	 * Gets authenticator id.
	 *
	 * @return the authenticator id
	 */
	public String getAuthenticatorId() {
		return authenticatorId;
	}

	/**
	 * Sets authenticator id.
	 *
	 * @param authenticatorId the authenticator id
	 */
	public void setAuthenticatorId(String authenticatorId) {
		this.authenticatorId = authenticatorId;
	}
	
	/**
	 * Is scan all values boolean.
	 *
	 * @return the boolean
	 */
	public boolean isScanAllValues() {
		return scanAllValues;
	}

	/**
	 * Sets scan all values.
	 *
	 * @param scanAllValues the scan all values
	 */
	public void setScanAllValues(boolean scanAllValues) {
		this.scanAllValues = scanAllValues;
	}

	/**
	 * Gets conjur mapping path.
	 *
	 * @return the conjur mapping path
	 */
	public String getMappingPath() {
		return mappingPath;
	}

	/**
	 * Sets conjur mapping path.
	 *
	 * @param mappingPath the conjur mapping path
	 */
	public void setMappingPath(String mappingPath) {
		this.mappingPath = mappingPath;
	}
	@Override
	public String toString() {
		return "ConjurProperties{" +
				"account='" + account + '\'' +
				", applianceUrl='" + applianceUrl + '\'' +
				", authTokenFile='" + authTokenFile + '\'' +
				", authnApiKey='" + authnApiKey + '\'' +
				", authnLogin='" + authnLogin + '\'' +
				", certFile='" + certFile + '\'' +
				", sslCertificate='" + sslCertificate + '\'' +
				", jwtTokenPath='" + jwtTokenPath + '\'' +
				", authenticatorId='" + authenticatorId + '\'' +
				", scanAllValues=" + scanAllValues +
				", conjurMappingPath='" + mappingPath + '\'' +
				", integrationName='" + integrationName + '\'' +
                ", integrationVersion='" + integrationVersion + '\'' +
                ", vendorName='" + vendorName + '\'' +
                ", integrationType='" + integrationType + '\'' +
				'}';
	}
}