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

package fr.devmind.mockwebserver;

import org.junit.jupiter.api.extension.*;
import org.junit.jupiter.api.extension.ExtensionContext.Store;

/**
 * Junit 5 extension used to inject a server in your tests. You can see an example on the Readme of this project https://github.com/Dev-Mind/mockwebserver.
 * This extension starts the server before each test and suhtdown after the test execution. If you want to manage the lifecycle you can use
 * {@link MockSimpleWebServerExtension}
 */
public final class MockWebServerExtension implements ParameterResolver, BeforeEachCallback, AfterEachCallback {

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
    public void beforeEach(ExtensionContext extensionContext) throws Exception {
        if (!webServer.isStarted()){
            webServer.start();
        }
    }

    @Override
    public void afterEach(ExtensionContext extensionContext) throws Exception {
        webServer.shutdown();
    }
}
