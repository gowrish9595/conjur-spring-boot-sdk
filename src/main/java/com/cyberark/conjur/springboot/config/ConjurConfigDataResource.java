package com.cyberark.conjur.springboot.config;

import java.util.Objects;

import org.springframework.boot.context.config.ConfigDataResource;

/**
 * Resource that identifies a Conjur vault path imported via
 * {@code spring.config.import: conjur://<path>}.
 */
public class ConjurConfigDataResource extends ConfigDataResource {

	private final String vaultPath;

	private final boolean optional;

	public ConjurConfigDataResource(String vaultPath, boolean optional) {
		super(optional);
		this.vaultPath = vaultPath == null ? "" : vaultPath;
		this.optional = optional;
	}

	public String getVaultPath() {
		return vaultPath;
	}

	public boolean isOptional() {
		return optional;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}
		ConjurConfigDataResource other = (ConjurConfigDataResource) obj;
		return optional == other.optional && Objects.equals(vaultPath, other.vaultPath);
	}

	@Override
	public int hashCode() {
		return Objects.hash(vaultPath, optional);
	}

	@Override
	public String toString() {
		return "conjur://" + vaultPath;
	}
}
