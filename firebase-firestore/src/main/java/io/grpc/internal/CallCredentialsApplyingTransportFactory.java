/*
 * Copyright 2016 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.internal;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;

import io.grpc.Attributes;
import io.grpc.CallCredentials;
import io.grpc.CallCredentials.RequestInfo;
import io.grpc.CallOptions;
import io.grpc.ChannelCredentials;
import io.grpc.ChannelLogger;
import io.grpc.ClientStreamTracer;
import io.grpc.CompositeCallCredentials;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.SecurityLevel;
import io.grpc.Status;
import java.net.SocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

final class CallCredentialsApplyingTransportFactory
    implements io.grpc.internal.ClientTransportFactory {
  private final io.grpc.internal.ClientTransportFactory delegate;
  private final CallCredentials channelCallCredentials;
  private final Executor appExecutor;

  CallCredentialsApplyingTransportFactory(
      ClientTransportFactory delegate,
      CallCredentials channelCallCredentials,
      Executor appExecutor) {
    this.delegate = checkNotNull(delegate, "delegate");
    this.channelCallCredentials = channelCallCredentials;
    this.appExecutor = checkNotNull(appExecutor, "appExecutor");
  }

  @Override
  public io.grpc.internal.ConnectionClientTransport newClientTransport(
      SocketAddress serverAddress, ClientTransportOptions options, ChannelLogger channelLogger) {
    return new CallCredentialsApplyingTransport(
        delegate.newClientTransport(serverAddress, options, channelLogger), options.getAuthority());
  }

  @Override
  public ScheduledExecutorService getScheduledExecutorService() {
    return delegate.getScheduledExecutorService();
  }

  @Override
  public SwapChannelCredentialsResult swapChannelCredentials(ChannelCredentials channelCreds) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void close() {
    delegate.close();
  }

  private class CallCredentialsApplyingTransport
      extends io.grpc.internal.ForwardingConnectionClientTransport {
    private final io.grpc.internal.ConnectionClientTransport delegate;
    private final String authority;

    CallCredentialsApplyingTransport(
        io.grpc.internal.ConnectionClientTransport delegate, String authority) {
      this.delegate = checkNotNull(delegate, "delegate");
      this.authority = checkNotNull(authority, "authority");
    }

    @Override
    protected ConnectionClientTransport delegate() {
      return delegate;
    }

    @Override
    @SuppressWarnings("deprecation")
    public ClientStream newStream(
        final MethodDescriptor<?, ?> method,
        Metadata headers,
        final CallOptions callOptions,
        ClientStreamTracer[] tracers) {
      CallCredentials creds = callOptions.getCredentials();
      if (creds == null) {
        creds = channelCallCredentials;
      } else if (channelCallCredentials != null) {
        creds = new CompositeCallCredentials(channelCallCredentials, creds);
      }
      if (creds != null) {
        io.grpc.internal.MetadataApplierImpl applier =
            new io.grpc.internal.MetadataApplierImpl(
                delegate, method, headers, callOptions, tracers);
        RequestInfo requestInfo =
            new RequestInfo() {
              @Override
              public MethodDescriptor<?, ?> getMethodDescriptor() {
                return method;
              }

              @Override
              public SecurityLevel getSecurityLevel() {
                return firstNonNull(
                    delegate.getAttributes().get(GrpcAttributes.ATTR_SECURITY_LEVEL),
                    SecurityLevel.NONE);
              }

              @Override
              public String getAuthority() {
                return firstNonNull(callOptions.getAuthority(), authority);
              }

              @Override
              public Attributes getTransportAttrs() {
                return delegate.getAttributes();
              }
            };
        try {
          creds.applyRequestMetadata(
              requestInfo, firstNonNull(callOptions.getExecutor(), appExecutor), applier);
        } catch (Throwable t) {
          applier.fail(
              Status.UNAUTHENTICATED
                  .withDescription("Credentials should use fail() instead of throwing exceptions")
                  .withCause(t));
        }
        return applier.returnStream();
      } else {
        return delegate.newStream(method, headers, callOptions, tracers);
      }
    }
  }
}