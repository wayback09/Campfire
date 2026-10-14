package org.gradle.accessors.dm;

import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.plugin.use.PluginDependency;
import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.provider.Provider;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.internal.catalog.AbstractExternalDependencyFactory;
import org.gradle.api.internal.catalog.DefaultVersionCatalog;
import java.util.Map;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParser;
import javax.inject.Inject;

/**
 * A catalog of dependencies accessible via the {@code libs} extension.
 */
@NonNullApi
public class LibrariesForLibs extends AbstractExternalDependencyFactory {

    private final AbstractExternalDependencyFactory owner = this;
    private final DiscordLibraryAccessors laccForDiscordLibraryAccessors = new DiscordLibraryAccessors(owner);
    private final FabricLibraryAccessors laccForFabricLibraryAccessors = new FabricLibraryAccessors(owner);
    private final JacksonLibraryAccessors laccForJacksonLibraryAccessors = new JacksonLibraryAccessors(owner);
    private final JavaLibraryAccessors laccForJavaLibraryAccessors = new JavaLibraryAccessors(owner);
    private final SqliteLibraryAccessors laccForSqliteLibraryAccessors = new SqliteLibraryAccessors(owner);
    private final VersionAccessors vaccForVersionAccessors = new VersionAccessors(providers, config);
    private final BundleAccessors baccForBundleAccessors = new BundleAccessors(objects, providers, config, attributesFactory, capabilityNotationParser);
    private final PluginAccessors paccForPluginAccessors = new PluginAccessors(providers, config);

    @Inject
    public LibrariesForLibs(DefaultVersionCatalog config, ProviderFactory providers, ObjectFactory objects, ImmutableAttributesFactory attributesFactory, CapabilityNotationParser capabilityNotationParser) {
        super(config, providers, objects, attributesFactory, capabilityNotationParser);
    }

    /**
     * Dependency provider for <b>hikari</b> with <b>com.zaxxer:HikariCP</b> coordinates and
     * with version reference <b>hikari</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getHikari() {
        return create("hikari");
    }

    /**
     * Dependency provider for <b>javalin</b> with <b>io.javalin:javalin</b> coordinates and
     * with version reference <b>javalin</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getJavalin() {
        return create("javalin");
    }

    /**
     * Dependency provider for <b>modmenu</b> with <b>com.terraformersmc:modmenu</b> coordinates and
     * with version reference <b>modmenu</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getModmenu() {
        return create("modmenu");
    }

    /**
     * Dependency provider for <b>weupnp</b> with <b>org.bitlet:weupnp</b> coordinates and
     * with version reference <b>weupnp</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getWeupnp() {
        return create("weupnp");
    }

    /**
     * Dependency provider for <b>yacl</b> with <b>dev.isxander:yet-another-config-lib</b> coordinates and
     * with version reference <b>yacl</b>
     * <p>
     * This dependency was declared in catalog libs.versions.toml
     */
    public Provider<MinimalExternalModuleDependency> getYacl() {
        return create("yacl");
    }

    /**
     * Group of libraries at <b>discord</b>
     */
    public DiscordLibraryAccessors getDiscord() {
        return laccForDiscordLibraryAccessors;
    }

    /**
     * Group of libraries at <b>fabric</b>
     */
    public FabricLibraryAccessors getFabric() {
        return laccForFabricLibraryAccessors;
    }

    /**
     * Group of libraries at <b>jackson</b>
     */
    public JacksonLibraryAccessors getJackson() {
        return laccForJacksonLibraryAccessors;
    }

    /**
     * Group of libraries at <b>java</b>
     */
    public JavaLibraryAccessors getJava() {
        return laccForJavaLibraryAccessors;
    }

    /**
     * Group of libraries at <b>sqlite</b>
     */
    public SqliteLibraryAccessors getSqlite() {
        return laccForSqliteLibraryAccessors;
    }

    /**
     * Group of versions at <b>versions</b>
     */
    public VersionAccessors getVersions() {
        return vaccForVersionAccessors;
    }

    /**
     * Group of bundles at <b>bundles</b>
     */
    public BundleAccessors getBundles() {
        return baccForBundleAccessors;
    }

    /**
     * Group of plugins at <b>plugins</b>
     */
    public PluginAccessors getPlugins() {
        return paccForPluginAccessors;
    }

    public static class DiscordLibraryAccessors extends SubDependencyFactory {

        public DiscordLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>ipc</b> with <b>com.jagrosh:DiscordIPC</b> coordinates and
         * with version reference <b>discordIpc</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getIpc() {
            return create("discord.ipc");
        }

    }

    public static class FabricLibraryAccessors extends SubDependencyFactory {

        public FabricLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>api</b> with <b>net.fabricmc.fabric-api:fabric-api</b> coordinates and
         * with version reference <b>fabricApi</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getApi() {
            return create("fabric.api");
        }

    }

    public static class JacksonLibraryAccessors extends SubDependencyFactory {

        public JacksonLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>databind</b> with <b>com.fasterxml.jackson.core:jackson-databind</b> coordinates and
         * with version reference <b>jackson</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getDatabind() {
            return create("jackson.databind");
        }

    }

    public static class JavaLibraryAccessors extends SubDependencyFactory {

        public JavaLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>jwt</b> with <b>com.auth0:java-jwt</b> coordinates and
         * with version reference <b>javaJwt</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getJwt() {
            return create("java.jwt");
        }

    }

    public static class SqliteLibraryAccessors extends SubDependencyFactory {

        public SqliteLibraryAccessors(AbstractExternalDependencyFactory owner) { super(owner); }

        /**
         * Dependency provider for <b>jdbc</b> with <b>org.xerial:sqlite-jdbc</b> coordinates and
         * with version reference <b>sqliteJdbc</b>
         * <p>
         * This dependency was declared in catalog libs.versions.toml
         */
        public Provider<MinimalExternalModuleDependency> getJdbc() {
            return create("sqlite.jdbc");
        }

    }

    public static class VersionAccessors extends VersionFactory  {

        public VersionAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

        /**
         * Version alias <b>discordIpc</b> with value <b>1.2.1</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getDiscordIpc() { return getVersion("discordIpc"); }

        /**
         * Version alias <b>fabricApi</b> with value <b>0.133.0+1.21.8</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getFabricApi() { return getVersion("fabricApi"); }

        /**
         * Version alias <b>hikari</b> with value <b>5.1.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getHikari() { return getVersion("hikari"); }

        /**
         * Version alias <b>jackson</b> with value <b>2.17.2</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getJackson() { return getVersion("jackson"); }

        /**
         * Version alias <b>javaJwt</b> with value <b>4.4.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getJavaJwt() { return getVersion("javaJwt"); }

        /**
         * Version alias <b>javalin</b> with value <b>6.3.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getJavalin() { return getVersion("javalin"); }

        /**
         * Version alias <b>modmenu</b> with value <b>14.0.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getModmenu() { return getVersion("modmenu"); }

        /**
         * Version alias <b>sqliteJdbc</b> with value <b>3.46.1.0</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getSqliteJdbc() { return getVersion("sqliteJdbc"); }

        /**
         * Version alias <b>weupnp</b> with value <b>0.1.4</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getWeupnp() { return getVersion("weupnp"); }

        /**
         * Version alias <b>yacl</b> with value <b>3.6.5+1.21.8-fabric</b>
         * <p>
         * If the version is a rich version and cannot be represented as a
         * single version string, an empty string is returned.
         * <p>
         * This version was declared in catalog libs.versions.toml
         */
        public Provider<String> getYacl() { return getVersion("yacl"); }

    }

    public static class BundleAccessors extends BundleFactory {

        public BundleAccessors(ObjectFactory objects, ProviderFactory providers, DefaultVersionCatalog config, ImmutableAttributesFactory attributesFactory, CapabilityNotationParser capabilityNotationParser) { super(objects, providers, config, attributesFactory, capabilityNotationParser); }

    }

    public static class PluginAccessors extends PluginFactory {

        public PluginAccessors(ProviderFactory providers, DefaultVersionCatalog config) { super(providers, config); }

    }

}
