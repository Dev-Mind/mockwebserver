/*
 * Copyright (C) 2011 Google Inc.
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.devmind.mockwebserver;

import org.junit.jupiter.api.extension.*;
import org.junit.jupiter.api.extension.ExtensionContext.Store;

import java.lang.reflect.Parameter;

/**
 * Junit 5 extension used to inject a server in your tests
 */
public final class MockWebServerExtension implements ParameterResolver, BeforeEachCallback, AfterAllCallback {

    private static final String STORE = "MWSStore";
    private static final String SERVER = "MWSServer";

    private MockWebServer webServer = new MockWebServer();

    /**
     * If you want to use a server in your tests you can inject an instance of this bean if you declare a property
     * with the annotation {@link MockWebServer}
     */
    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        return parameterContext.getParameter().getType() == MockWebServer.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Store mocks = extensionContext.getStore(ExtensionContext.Namespace.create(MockWebServerExtension.class, STORE));
        return mocks.getOrComputeIfAbsent(SERVER, key -> webServer);
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {
        webServer.shutdown();
    }

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        if (!webServer.isStarted()){
            webServer.start();
        }
    }
}
