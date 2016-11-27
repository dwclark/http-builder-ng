/*
 * Copyright (C) 2016 David Clark
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package groovyx.net.http.tk

import groovyx.net.http.ChainedHttpConfig
import groovyx.net.http.FromServer
import groovyx.net.http.HttpBin
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import spock.lang.Ignore
import spock.lang.Requires

import java.util.concurrent.Executors

import static groovyx.net.http.ContentTypes.TEXT

/**
 * Test kit for testing the HTTP DELETE method with different clients.
 */
abstract class HttpDeleteTestKit extends HttpMethodTestKit {

    private static final String DATE_STRING = '2016.08.25 14:43'

    def 'DELETE /: returns content'() {
        setup:
        serverRule.dispatcher('DELETE', '/', responseContent())

        expect:
        httpBuilder(serverRule.serverPort).delete() == htmlContent()

        and:
        httpBuilder(serverRule.serverPort).deleteAsync().get() == htmlContent()
    }

    def 'DELETE /foo: returns content'() {
        given:
        serverRule.dispatcher('DELETE', '/foo', responseContent())

        def config = {
            request.uri.path = '/foo'
        }

        expect:
        httpBuilder(serverRule.serverPort).delete(config) == htmlContent()

        and:
        httpBuilder(serverRule.serverPort).deleteAsync(config).get() == htmlContent()
    }

    def 'DELETE /foo (cookie): returns content'() {
        given:
        serverRule.dispatcher { RecordedRequest request ->
            if (request.method == 'DELETE' && request.path == '/foo' && request.getHeader('Cookie').contains('userid=spock')) {
                return responseContent()
            }
            return new MockResponse().setResponseCode(404)
        }

        def config = {
            request.uri.path = '/foo'
            request.cookie 'userid', 'spock'
        }

        expect:
        httpBuilder(serverRule.serverPort).delete(config) == htmlContent()

        and:
        httpBuilder(serverRule.serverPort).deleteAsync(config).get() == htmlContent()
    }

    def 'DELETE /foo (query string): returns content'() {
        given:
        serverRule.dispatcher('DELETE', '/foo?action=login', responseContent())

        def config = {
            request.uri.path = '/foo'
            request.uri.query = [action: 'login']
            request.contentType = TEXT[0]
        }

        expect:
        httpBuilder(serverRule.serverPort).delete(config) == htmlContent()

        and:
        httpBuilder(serverRule.serverPort).deleteAsync(config).get() == htmlContent()
    }

    def 'DELETE /date: returns content as Date'() {
        given:
        serverRule.dispatcher('DELETE', '/date', new MockResponse().setHeader('Content-Type', 'text/date').setBody(DATE_STRING))

        def config = {
            request.uri.path = '/date'
            response.parser('text/date') { ChainedHttpConfig config, FromServer fromServer ->
                Date.parse('yyyy.MM.dd HH:mm', fromServer.inputStream.text)
            }
        }

        expect:
        httpBuilder(serverRule.serverPort).delete(Date, config).format('yyyy.MM.dd HH:mm') == DATE_STRING

        and:
        httpBuilder(serverRule.serverPort).deleteAsync(Date, config).get().format('yyyy.MM.dd HH:mm') == DATE_STRING
    }

    def 'DELETE (BASIC) /basic: returns only headers'() {
        given:
        serverRule.dispatcher { RecordedRequest request ->
            if (request.method == 'DELETE') {
                String encodedCred = "Basic ${'admin:$3cr3t'.bytes.encodeBase64()}"

                if (request.path == '/basic' && !request.getHeader('Authorization')) {
                    return new MockResponse().setHeader('WWW-Authenticate', 'Basic realm="Test Realm"').setResponseCode(401)
                } else if (request.path == '/basic' && request.getHeader('Authorization') == encodedCred) {
                    return new MockResponse().setHeader('Authorization', encodedCred).setHeader('Content-Type', 'text/plain').setBody(htmlContent())
                }
            }
            return new MockResponse().setResponseCode(404)
        }

        def config = {
            request.uri.path = '/basic'
            request.auth.basic 'admin', '$3cr3t'
        }

        expect:
        httpBuilder(serverRule.serverPort).delete(config) == htmlContent()

        and:
        httpBuilder(serverRule.serverPort).deleteAsync(config).get() == htmlContent()
    }

    protected static MockResponse responseContent(final String body = htmlContent()) {
        new MockResponse().setHeader('Content-Type', 'text/plain').setBody(body)
    }
}
