/**
 *
 * Copyright (C) 2009 Cloud Conscious, LLC. <info@cloudconscious.com>
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */
package org.jclouds.rest;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.toArray;
import static org.jclouds.util.Utils.initContextBuilder;
import static org.jclouds.util.Utils.propagateAuthorizationOrOriginalException;
import static org.jclouds.util.Utils.resolveContextBuilderClass;
import static org.jclouds.util.Utils.resolvePropertiesBuilderClass;

import java.io.IOException;
import java.util.Properties;

import javax.annotation.Nullable;
import javax.inject.Inject;

import org.jclouds.PropertiesBuilder;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;

/**
 * Helper class to instantiate {@code RestContext} instances. "blobstore.properties"
 * 
 * At least one property is needed needed per context:
 * <ul>
 * <li>tag.contextbuilder=classname extends RestContextBuilder</li>
 * </ul>
 * 
 * Optional properties are as follows
 * <ul>
 * <li>tag.contextbuilder=classname extends RestContextBuilder</li>
 * <li>tag.propertiesbuilder=classname extends HttpPropertiesBuilder</li>
 * </ul>
 * Ex.
 * 
 * <pre>
 * azureblob.contextbuilder=org.jclouds.azure.storage.blob.blobstore.AzureRestContextBuilder
 * azureblob.propertiesbuilder=org.jclouds.azure.storage.blob.AzureBlobPropertiesBuilder
 * </pre>
 * 
 * @author Adrian Cole
 */
public class RestContextFactory {

   public static <S, A> ContextSpec<S, A> contextSpec(String provider, String endpoint,
            String apiVersion, String identity, String credential, Class<S> sync, Class<A> async,
            Class<PropertiesBuilder> propertiesBuilderClass,
            Class<RestContextBuilder<S, A>> contextBuilderClass) {
      return new ContextSpec<S, A>(provider, endpoint, apiVersion, identity, credential, sync,
               async, propertiesBuilderClass, contextBuilderClass);
   }

   public static <S, A> ContextSpec<S, A> contextSpec(String provider, String endpoint,
            String apiVersion, String identity, String credential, Class<S> sync, Class<A> async) {
      return new ContextSpec<S, A>(provider, endpoint, apiVersion, identity, credential, sync,
               async);
   }

   public static class ContextSpec<S, A> {
      final String provider;
      final String endpoint;
      final String apiVersion;
      final String identity;
      final String credential;
      final Class<S> sync;
      final Class<A> async;
      final Class<PropertiesBuilder> propertiesBuilderClass;
      final Class<RestContextBuilder<S, A>> contextBuilderClass;

      ContextSpec(String provider, String endpoint, String apiVersion, String identity,
               String credential, Class<S> sync, Class<A> async,
               Class<PropertiesBuilder> propertiesBuilderClass,
               Class<RestContextBuilder<S, A>> contextBuilderClass) {
         this.provider = checkNotNull(provider, "provider");
         this.endpoint = endpoint;
         this.apiVersion = apiVersion;
         this.identity = identity;
         this.credential = credential;
         this.sync = sync;
         this.async = async;
         this.propertiesBuilderClass = propertiesBuilderClass;
         this.contextBuilderClass = contextBuilderClass;
      }

      @SuppressWarnings("unchecked")
      public ContextSpec(String provider, String endpoint, String apiVersion, String identity,
               String credential, Class<S> sync, Class<A> async) {
         this(provider, endpoint, apiVersion, identity, credential, sync, async,
                  PropertiesBuilder.class, (Class) RestContextBuilder.class);
      }
   }

   private final static Properties NO_PROPERTIES = new Properties();
   private final Properties properties;

   /**
    * Initializes with the default properties built-in to jclouds. This is typically stored in the
    * classpath resource {@code rest.properties}
    * 
    * @see RestContextFactory#getPropertiesFromResource
    */
   public RestContextFactory() {
      this("/rest.properties");
   }

   /**
    * Initializes with the default properties built-in to jclouds. This is typically stored in the
    * classpath resource {@code filename}
    * 
    * @param filename
    *           name of the properties file to initialize from
    * @throws IOException
    *            if the default properties file cannot be loaded
    * @see #getPropertiesFromResource
    */
   public RestContextFactory(String filename) {
      this(getPropertiesFromResource(filename));
   }

   /**
    * Loads the default properties that define the {@code RestContext} objects. <h3>properties file
    * format</h3>
    * 
    * Two properties are needed per context:
    * <ul>
    * <li>tag.contextbuilder=classname extends RestContextBuilder</li>
    * <li>tag.propertiesbuilder=classname extends HttpPropertiesBuilder</li>
    * </ul>
    * Ex.
    * 
    * <pre>
    * azureblob.contextbuilder=org.jclouds.azure.storage.blob.blobstore.AzureRestContextBuilder
    * azureblob.propertiesbuilder=org.jclouds.azure.storage.blob.AzureBlobPropertiesBuilder
    * </pre>
    * 
    * @param filename
    *           name of file to load from in the resource path
    * @return properties object with these items loaded for each tag
    */
   public static Properties getPropertiesFromResource(String filename) {
      Properties properties = new Properties();
      try {
         properties.load(RestContextFactory.class.getResourceAsStream(filename));
      } catch (IOException e) {
         Throwables.propagate(e);
      }
      properties.putAll(System.getProperties());
      return properties;
   }

   /**
    * Initializes the {@code RestContext} definitions from the specified properties.
    */
   @Inject
   public RestContextFactory(Properties properties) {
      this.properties = properties;
   }

   public <S, A> RestContextBuilder<S, A> createContextBuilder(String provider, String identity,
            String credential) {
      return createContextBuilder(provider, identity, credential, ImmutableSet.<Module> of(),
               NO_PROPERTIES);
   }

   /**
    * @see RestContextFactory#createContextBuilder(String, Properties, Iterable<? extends Module>, Properties)
    */
   public <S, A> RestContextBuilder<S, A> createContextBuilder(String provider, Properties overrides) {
      return createContextBuilder(provider, overrides.getProperty("jclouds.identity"), overrides
               .getProperty("jclouds.credential"), ImmutableSet.<Module> of(), overrides);
   }

   /**
    * 
    * Identity will be found by searching {@code jclouds.identity} failing that {@code
    * provider.identity} where provider corresponds to the parameter. Same pattern is used for
    * credential ({@code jclouds.credential} failing that {@code provider.credential}).
    * 
    * @param <S>
    *           Type of the provider specific client
    * @param <A>
    *           Type of the provide specific async client (same as above, yet all methods return
    *           {@code Future} results)
    * @param provider
    *           name of the provider (ex. s3, bluelock, etc.)
    * @param wiring
    *           defines how objects are bound to interfaces, pass in here to override this, or
    *           specify service implementations.
    * @param overrides
    *           properties to pass to the context.
    */
   public <S, A> RestContextBuilder<S, A> createContextBuilder(String provider,
            Iterable<? extends Module> wiring, Properties overrides) {
      return createContextBuilder(provider, overrides.getProperty("jclouds.identity"), overrides
               .getProperty("jclouds.credential"), wiring, overrides);
   }

   public <S, A> RestContextBuilder<S, A> createContextBuilder(String provider,
            @Nullable String identity, @Nullable String credential,
            Iterable<? extends Module> wiring) {
      return createContextBuilder(provider, identity, credential, wiring, new Properties());
   }

   /**
    * Creates a new remote context.
    * 
    * @param provider
    * @param identity
    *           nullable, if credentials are present in the overrides
    * @param credential
    *           nullable, if credentials are present in the overrides
    * @param wiring
    *           Configuration you'd like to pass to the context. Ex. ImmutableSet.<Module>of(new
    *           ExecutorServiceModule(myexecutor))
    * @param overrides
    *           properties to override defaults with.
    * @return initialized context ready for use
    */
   public <S, A> RestContextBuilder<S, A> createContextBuilder(String providerName,
            @Nullable String identity, @Nullable String credential,
            Iterable<? extends Module> wiring, Properties _overrides) {
      checkNotNull(wiring, "wiring");
      ContextSpec<S, A> contextSpec = createContextSpec(providerName, identity, credential,
               _overrides);
      return createContextBuilder(contextSpec, wiring, _overrides);
   }

   @SuppressWarnings("unchecked")
   public <A, S> ContextSpec<S, A> createContextSpec(String providerName, String identity,
            String credential, Properties _overrides) {
      checkNotNull(providerName, "providerName");
      checkNotNull(_overrides, "overrides");

      Properties props = new Properties();
      props.putAll(this.properties);
      props.putAll(_overrides);

      String endpoint = props.getProperty(providerName + ".endpoint", null);
      String apiVersion = props.getProperty(providerName + ".apiversion", null);
      identity = props.getProperty(providerName + ".identity", identity);
      credential = props.getProperty(providerName + ".credential", credential);
      String syncClassName = props.getProperty(providerName + ".sync", null);
      String asyncClassName = props.getProperty(providerName + ".async", null);

      Class<RestContextBuilder<S, A>> contextBuilderClass;
      Class<PropertiesBuilder> propertiesBuilderClass;
      Class<S> sync;
      Class<A> async;
      try {
         contextBuilderClass = resolveContextBuilderClass(providerName, props);
         propertiesBuilderClass = resolvePropertiesBuilderClass(providerName, props);
         sync = (Class<S>) (syncClassName != null ? Class.forName(syncClassName) : null);
         async = (Class<A>) (syncClassName != null ? Class.forName(asyncClassName) : null);
      } catch (Exception e) {
         Throwables.propagate(e);
         assert false : "exception should have propogated " + e;
         return null;
      }

      ContextSpec<S, A> contextSpec = new ContextSpec<S, A>(providerName, endpoint, apiVersion,
               identity, credential, sync, async, propertiesBuilderClass, contextBuilderClass);
      return contextSpec;
   }

   public static <S, A> RestContextBuilder<S, A> createContextBuilder(ContextSpec<S, A> contextSpec) {
      return createContextBuilder(contextSpec, new Properties());
   }

   public static <S, A> RestContextBuilder<S, A> createContextBuilder(
            ContextSpec<S, A> contextSpec, Properties overrides) {
      return createContextBuilder(contextSpec, ImmutableSet.<Module> of(), overrides);
   }

   public static <S, A> RestContextBuilder<S, A> createContextBuilder(
            ContextSpec<S, A> contextSpec, Iterable<? extends Module> wiring) {
      return createContextBuilder(contextSpec, wiring, new Properties());
   }

   public static <S, A> RestContextBuilder<S, A> createContextBuilder(
            ContextSpec<S, A> contextSpec, Iterable<? extends Module> wiring, Properties overrides) {
      try {
         PropertiesBuilder builder = contextSpec.propertiesBuilderClass.getConstructor(
                  Properties.class).newInstance(overrides);

         builder.provider(contextSpec.provider);
         if (contextSpec.apiVersion != null)
            builder.apiVersion(contextSpec.apiVersion);
         if (contextSpec.identity != null)
            builder.credentials(contextSpec.identity, contextSpec.credential);
         if (contextSpec.endpoint != null)
            builder.endpoint(contextSpec.endpoint);

         RestContextBuilder<S, A> contextBuilder = initContextBuilder(
                  contextSpec.contextBuilderClass, contextSpec.sync, contextSpec.async, builder
                           .build());

         contextBuilder.withModules(toArray(wiring, Module.class));

         return contextBuilder;
      } catch (Exception e) {
         return propagateAuthorizationOrOriginalException(e);
      }
   }

   /**
    * @see RestContextFactory#createContextBuilder(String, String, String)
    */
   public <S, A> RestContext<S, A> createContext(String provider, String identity, String credential) {
      RestContextBuilder<S, A> builder = createContextBuilder(provider, identity, credential);
      return buildContextUnwrappingExceptions(builder);
   }

   public static <S, A> RestContext<S, A> buildContextUnwrappingExceptions(
            RestContextBuilder<S, A> builder) {
      try {
         return builder.buildContext();
      } catch (Exception e) {
         return propagateAuthorizationOrOriginalException(e);
      }
   }

   /**
    * @see RestContextFactory#createContextBuilder(String, Properties)
    */
   public <S, A> RestContext<S, A> createContext(String provider, Properties overrides) {
      RestContextBuilder<S, A> builder = createContextBuilder(provider, overrides);
      return buildContextUnwrappingExceptions(builder);
   }

   /**
    * @see RestContextFactory#createContextBuilder(String, Iterable)
    */
   public <S, A> RestContext<S, A> createContext(String provider,
            Iterable<? extends Module> wiring, Properties overrides) {
      RestContextBuilder<S, A> builder = createContextBuilder(provider, wiring, overrides);
      return buildContextUnwrappingExceptions(builder);
   }

   /**
    * @see RestContextFactory#createContextBuilder(String, String,String, Iterable)
    */
   public <S, A> RestContext<S, A> createContext(String provider, @Nullable String identity,
            @Nullable String credential, Iterable<? extends Module> wiring) {
      RestContextBuilder<S, A> builder = createContextBuilder(provider, identity, credential,
               wiring);
      return buildContextUnwrappingExceptions(builder);
   }

   /**
    * @see RestContextFactory#createContextBuilder(String, String,String, Iterable, Properties)
    */
   public <S, A> RestContext<S, A> createContext(String provider, @Nullable String identity,
            @Nullable String credential, Iterable<? extends Module> wiring, Properties overrides) {
      RestContextBuilder<S, A> builder = createContextBuilder(provider, identity, credential,
               wiring, overrides);
      return buildContextUnwrappingExceptions(builder);
   }

   /**
    * @see RestContextFactory#createContextBuilder(ContextSpec, Iterable, Properties)
    */
   public static <S, A> RestContext<S, A> createContext(ContextSpec<S, A> contextSpec,
            Iterable<? extends Module> wiring, Properties overrides) {
      RestContextBuilder<S, A> builder = createContextBuilder(contextSpec, wiring, overrides);
      return buildContextUnwrappingExceptions(builder);
   }

   /**
    * @see RestContextFactory#createContextBuilder(ContextSpec)
    */
   public static <S, A> RestContext<S, A> createContext(ContextSpec<S, A> contextSpec) {
      RestContextBuilder<S, A> builder = createContextBuilder(contextSpec);
      return buildContextUnwrappingExceptions(builder);
   }

   /**
    * @see RestContextFactory#createContextBuilder(ContextSpec, Iterable)
    */
   public static <S, A> RestContext<S, A> createContext(ContextSpec<S, A> contextSpec,
            Iterable<? extends Module> wiring) {
      RestContextBuilder<S, A> builder = createContextBuilder(contextSpec, wiring);
      return buildContextUnwrappingExceptions(builder);
   }

   /**
    * @see RestContextFactory#createContextBuilder(ContextSpec, Properties)
    */
   public static <S, A> RestContext<S, A> createContext(ContextSpec<S, A> contextSpec,
            Properties overrides) {
      RestContextBuilder<S, A> builder = createContextBuilder(contextSpec, overrides);
      return buildContextUnwrappingExceptions(builder);
   }
}
