/**
 * Licensed to jclouds, Inc. (jclouds) under one or more
 * contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  jclouds licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jclouds.atmos.filters;

import static com.google.common.base.Throwables.propagate;
import static org.jclouds.Constants.LOGGER_SIGNATURE;
import static org.jclouds.crypto.CryptoStreams.base64;
import static org.jclouds.crypto.CryptoStreams.mac;
import static org.jclouds.http.Uris.uriBuilder;

import java.io.IOException;
import java.net.URI;
import java.security.InvalidKeyException;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.jclouds.crypto.Crypto;
import org.jclouds.date.TimeStamp;
import org.jclouds.http.HttpException;
import org.jclouds.io.InputSuppliers;
import org.jclouds.location.Provider;
import org.jclouds.logging.Logger;
import org.jclouds.rest.annotations.Credential;
import org.jclouds.rest.annotations.Identity;

import com.google.common.base.Function;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;

/**
 * Signs the EMC Atmos Online Storage request.
 * 
 * @see <a href="https://community.emc.com/community/labs/atmos_online" />
 * @author Adrian Cole
 * 
 */
@Singleton
public class ShareUrl implements Function<String, URI> {

   private final String uid;
   private final byte[] key;
   private final Supplier<URI> provider;
   private final javax.inject.Provider<Long> timeStampProvider;
   private final Crypto crypto;

   @Resource
   Logger logger = Logger.NULL;

   @Resource
   @Named(LOGGER_SIGNATURE)
   Logger signatureLog = Logger.NULL;

   @Inject
   public ShareUrl(@Identity String uid, @Credential String encodedKey,
            @Provider Supplier<URI> provider, @TimeStamp javax.inject.Provider<Long> timeStampProvider, Crypto crypto) {
      this.uid = uid;
      this.key = base64(encodedKey);
      this.provider = provider;
      this.timeStampProvider = timeStampProvider;
      this.crypto = crypto;
   }

   @Override
   public URI apply(String path) throws HttpException {
      String requestedResource = new StringBuilder().append("/rest/namespace/").append(path).toString();
      String expires = timeStampProvider.get().toString();
      String signature = signString(createStringToSign(requestedResource, expires));
      return uriBuilder(provider.get())
            .replaceQuery(ImmutableMap.of("uid", uid, "expires", expires, "signature", signature))
            .appendPath(requestedResource).build();
   }

   public String createStringToSign(String requestedResource, String expires) {
      StringBuilder toSign = new StringBuilder();
      toSign.append("GET\n");
      toSign.append(requestedResource.toLowerCase()).append("\n");
      toSign.append(uid).append("\n");
      toSign.append(expires);
      return toSign.toString();
   }

   public String signString(String toSign) {
      try {
         return base64(mac(InputSuppliers.of(toSign), crypto.hmacSHA1(key)));
      } catch (InvalidKeyException e) {
         throw propagate(e);
      } catch (IOException e) {
         throw propagate(e);
      }
   }

}
