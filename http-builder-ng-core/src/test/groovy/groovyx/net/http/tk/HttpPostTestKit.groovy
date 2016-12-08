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

import com.stehno.ersatz.feat.BasicAuthFeature
import groovy.json.JsonSlurper
import groovyx.net.http.ChainedHttpConfig
import groovyx.net.http.FromServer
import groovyx.net.http.NativeHandlers
import groovyx.net.http.ToServer
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Unroll

import static groovyx.net.http.ContentTypes.JSON
import static groovyx.net.http.ContentTypes.TEXT

/**
 * Test kit for testing the HTTP POST method with different clients.
 */
abstract class HttpPostTestKit extends HttpMethodTestKit {

    private static final String DATE_STRING = '2016.08.25 14:43'
    private static final Date DATE_OBJECT = Date.parse('yyyy.MM.dd HH:mm', DATE_STRING)
    private static final String JSON_STRING = '{"name":"Bob","age":42}'
    private static final String BODY_STRING = 'This is CONTENT!!'
    private static final String HTML_CONTENT = htmlContent('Something Different')
    private static final String JSON_CONTENT = '{ "accepted":true, "id":100 }'

    def 'POST /: returns content'() {
        setup:
        ersatzServer.expectations {
            post('/').responds().content(htmlContent(), 'text/plain')
        }.start()

        expect:
        httpBuilder(ersatzServer.port).post() == htmlContent()

        and:
        httpBuilder(ersatzServer.port).postAsync().get() == htmlContent()
    }

    @Unroll def 'POST /foo (#contentType): returns content'() {
        given:
        ersatzServer.expectations {
            post('/foo').body(BODY_STRING, TEXT[0]).responds().content(HTML_CONTENT, TEXT[0])

            post('/foo') {
                body([age: 42, name: 'Bob'], JSON[0])
                converter(JSON[0] as String, { b -> new JsonSlurper().parse(b) })
                responder {
                    content('{"name":"Bob","age":42}', JSON[0])
                    //                    content(JSON_STRING, JSON[0]) // TODO: bug in ersatz?
                }
            }
        }.start()

        def config = {
            request.uri.path = '/foo'
            request.body = requestContent
            request.contentType = contentType[0]
            response.parser contentType, parser
        }

        expect:
        httpBuilder(ersatzServer.port).post(config) == result

        and:
        httpBuilder(ersatzServer.port).postAsync(config).get() == result

        where:
        requestContent | contentType | parser                               || result
        BODY_STRING    | TEXT        | NativeHandlers.Parsers.&textToString || HTML_CONTENT
        JSON_STRING    | JSON        | NativeHandlers.Parsers.&json         || [age: 42, name: 'Bob']
    }

    @Unroll def 'POST /foo (#contentType): encodes and decodes properly and returns content'() {
        given:
        ersatzServer.expectations {
            post('/foo').body([name: 'Bob', age: 42], JSON[0]).responds().content(JSON_CONTENT, JSON[0])
        }.start()

        def config = {
            request.uri.path = '/foo'
            request.body = content
            request.contentType = contentType[0]
        }

        expect:
        httpBuilder(ersatzServer.port).post(config) == result

        and:
        httpBuilder(ersatzServer.port).postAsync(config).get() == result

        where:
        contentType | content                || result
        JSON        | [name: 'Bob', age: 42] || [accepted: true, id: 100]
        JSON        | { name 'Bob'; age 42 } || [accepted: true, id: 100]
    }

    @Issue('https://github.com/http-builder-ng/http-builder-ng/issues/49')
    def 'POST /foo (cookie): returns content'() {
        given:
        ersatzServer.expectations {
            post('/foo').body(BODY_STRING, TEXT[0]).cookie('userid', 'spock').responds().content(htmlContent(), TEXT[0])
        }.start()

        def config = {
            request.uri.path = '/foo'
            request.body = BODY_STRING
            request.contentType = TEXT[0]
            request.cookie 'userid', 'spock'
        }

        expect:
        httpBuilder(ersatzServer.port).post(config) == htmlContent()

        and:
        httpBuilder(ersatzServer.port).postAsync(config).get() == htmlContent()
    }

    def 'POST /foo (query string): returns content'() {
        given:
        ersatzServer.expectations {
            post('/foo').body(BODY_STRING, TEXT[0]).query('action', 'login').responds().content(htmlContent(), TEXT[0])
        }.start()

        def config = {
            request.uri.path = '/foo'
            request.uri.query = [action: 'login']
            request.body = BODY_STRING
            request.contentType = TEXT[0]
        }

        expect:
        httpBuilder(ersatzServer.port).post(config) == htmlContent()

        and:
        httpBuilder(ersatzServer.port).postAsync(config).get() == htmlContent()
    }

    def 'POST /date: returns content as Date'() {
        given:
        ersatzServer.expectations {
            post('/date').body('DATE-TIME: 20160825-1443', 'text/datetime').responds().content(DATE_STRING, 'text/date')
        }.start()

        def config = {
            request.uri.path = '/date'
            request.body = DATE_OBJECT
            request.contentType = 'text/datetime'
            request.encoder('text/datetime') { ChainedHttpConfig config, ToServer req ->
                req.toServer(new ByteArrayInputStream("DATE-TIME: ${config.request.body.format('yyyyMMdd-HHmm')}".bytes))
            }
            response.parser('text/date') { ChainedHttpConfig config, FromServer fromServer ->
                Date.parse('yyyy.MM.dd HH:mm', fromServer.inputStream.text)
            }
        }

        expect:
        httpBuilder(ersatzServer.port).post(Date, config).format('yyyy.MM.dd HH:mm') == DATE_STRING

        and:
        httpBuilder(ersatzServer.port).postAsync(Date, config).get().format('yyyy.MM.dd HH:mm') == DATE_STRING
    }

    @Ignore @Issue('https://github.com/http-builder-ng/http-builder-ng/issues/10')
    def '[#client] POST (BASIC) /basic: returns content'() {
        setup:
        ersatzServer.addFeature new BasicAuthFeature()

        ersatzServer.expectations {
            post('/basic').body([name: 'Bob', age: 42], JSON[0]).responds().content(htmlContent(), 'text/plain')
        }.start()

        expect:
        httpBuilder(ersatzServer.port).post({
            request.uri.path = '/basic'
            request.body = JSON_STRING
            request.contentType = JSON[0]
            request.auth.basic 'admin', '$3cr3t'
        }) == htmlContent()

        and:
        httpBuilder(ersatzServer.port).postAsync({
            request.uri.path = '/basic'
            request.body = JSON_STRING
            request.contentType = JSON[0]
            request.auth.basic 'admin', '$3cr3t'
        }).get() == htmlContent()
    }
}
